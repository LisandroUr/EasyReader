package com.example.easyreader.data

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.easyreader.util.BookFormatUtils
import com.example.easyreader.util.PdfHelper
import com.example.easyreader.util.ZipHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class LibraryRepository(private val context: Context) {
    private val database = EasyReaderDatabase.getDatabase(context)
    private val seriesDao = database.seriesDao()
    private val bookDao = database.bookDao()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Observers para carpetas vinculadas
    private val folderObservers = mutableMapOf<String, ContentObserver>()
    // Evita reentrancy / import simultáneo por carpeta
    private val folderMutexes = ConcurrentHashMap<String, Mutex>()
    // Evita que varios imports se ejecuten simultáneamente en toda la app
    private val globalMutex = Mutex()
    // Series actualmente en proceso de escaneo (por id)
    private val scanningSeries = MutableStateFlow<Set<Long>>(emptySet())
    fun getScanningSeries(): Flow<Set<Long>> = scanningSeries.asStateFlow()
    // Callbacks pendientes (para debounce)
    private val pendingRunnables = mutableMapOf<String, Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val debounceDelayMs = 2000L
    companion object {
        private const val TAG = "LibraryRepository"
    }

    fun getAllSeries(): Flow<List<Series>> = seriesDao.getAllSeries()
    
    fun getSeriesById(id: Long): Flow<Series?> = seriesDao.getSeriesById(id)

    suspend fun insertSeries(series: Series): Long = seriesDao.insertSeries(series)

    suspend fun updateSeries(series: Series) = seriesDao.updateSeries(series)

    suspend fun deleteSeries(series: Series) = seriesDao.deleteSeries(series)

    fun getBooksBySeries(seriesId: Long): Flow<List<Book>> = bookDao.getBooksBySeries(seriesId)

    fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()

    fun getBookById(id: Long): Flow<Book?> = bookDao.getBookById(id)

    suspend fun removeDuplicateBooksFromSeries(seriesId: Long): Int = withContext(Dispatchers.IO) {
        val books = bookDao.getBooksBySeries(seriesId).first()
            .sortedWith(compareBy<Book> { it.customOrder }.thenBy { it.id })

        val duplicates = books.groupBy { it.pdfUri }
            .values
            .flatMap { group -> group.drop(1) }

        duplicates.forEach { bookDao.deleteBook(it) }
        duplicates.size
    }

    suspend fun removeNonPdfBooksFromLibrary(): Int = withContext(Dispatchers.IO) {
        val seriesList = seriesDao.getAllSeries().first()
        var deleted = 0
        seriesList.forEach { series ->
            deleted += removeNonPdfBooksFromSeries(series.id)
        }
        deleted
    }

    suspend fun removeEmptySeriesFromLibrary(): Int = withContext(Dispatchers.IO) {
        val seriesList = seriesDao.getAllSeries().first()
        var deleted = 0
        seriesList.forEach { series ->
            val hasBooks = bookDao.getBooksBySeries(series.id).first().isNotEmpty()
            if (!hasBooks) {
                seriesDao.deleteSeries(series)
                deleted++
            }
        }
        deleted
    }

    private suspend fun removeNonPdfBooksFromSeries(seriesId: Long): Int {
        val books = bookDao.getBooksBySeries(seriesId).first()
        val nonPdfBooks = books.filterNot { isPdfBookUri(it.pdfUri) }
        nonPdfBooks.forEach { bookDao.deleteBook(it) }
        return nonPdfBooks.size
    }

    private fun isPdfBookUri(uriString: String): Boolean {
        return try {
            val uri = uriString.toUri()
            BookFormatUtils.isPdfUri(context, uri)
        } catch (_: Exception) {
            BookFormatUtils.isPdf(null, uriString)
        }
    }

    suspend fun insertBook(book: Book): Long {
        val maxOrder = bookDao.getMaxOrder(book.seriesId) ?: -1
        return bookDao.insertBook(book.copy(customOrder = maxOrder + 1))
    }

    suspend fun insertBookIfMissing(book: Book): Long? {
        val existing = bookDao.getBookBySeriesAndPdfUri(book.seriesId, book.pdfUri)
        return if (existing == null) insertBook(book) else null
    }

    suspend fun importUriIfSupported(seriesId: Long, uri: Uri): Long? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        if (!BookFormatUtils.isPdfUri(context, uri)) return@withContext null

        val existing = bookDao.getBookBySeriesAndPdfUri(seriesId, uriString)
        if (existing != null) return@withContext null
        val book = createBookFromUri(
            seriesId = seriesId,
            uri = uri,
            mimeType = context.contentResolver.getType(uri),
            name = uri.lastPathSegment
        ) ?: return@withContext null
        insertBookIfMissing(book)
    }

    suspend fun updateBook(book: Book) = bookDao.updateBook(book)

    suspend fun deleteBook(book: Book) = bookDao.deleteBook(book)

    fun getRecentlyReadBooks(): Flow<List<Book>> = bookDao.getRecentlyReadBooks()

    fun searchBooks(query: String): Flow<List<Book>> = bookDao.searchBooks(query)

    fun searchSeries(query: String): Flow<List<Series>> = seriesDao.searchSeries(query)

    fun takePersistableUriPermission(uri: Uri) {
        val contentResolver = context.contentResolver
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            return
        } catch (_: Exception) {}

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
    }

    suspend fun bindFolderToSeries(seriesId: Long, folderUri: Uri) {
        takePersistableUriPermission(folderUri)
        val directory = DocumentFile.fromTreeUri(context, folderUri) ?: return
        // Actualizar la serie con la carpeta vinculada
        try {
            val series = seriesDao.getSeriesById(seriesId).first() ?: return
            seriesDao.updateSeries(series.copy(folderUri = folderUri.toString()))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Importar contenidos actuales
        val key = folderUri.toString()
        val folderMutex = folderMutexes.getOrPut(key) { Mutex() }
        globalMutex.withLock {
            folderMutex.withLock {
                scanningSeries.value = scanningSeries.value + seriesId
                try {
                    importRecursively(seriesId, directory)
                } finally {
                    scanningSeries.value = scanningSeries.value - seriesId
                }
            }
        }
    }

    fun startWatchingFolder(seriesId: Long, folderUri: Uri) {
        val key = folderUri.toString()
        if (folderObservers.containsKey(key)) return

        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Si ya estamos procesando esta carpeta, ignoramos eventos inmediatos
                val folderMutex = folderMutexes.getOrPut(key) { Mutex() }
                if (folderMutex.isLocked) {
                    Log.d(TAG, "onChange ignored - processing in progress for $key")
                    return
                }

                // Debounce: cancelar runnable previo y programar uno nuevo
                pendingRunnables[key]?.let { existing ->
                    try { mainHandler.removeCallbacks(existing) } catch (_: Exception) {}
                }

                var runnable: Runnable? = null
                runnable = Runnable {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            if (globalMutex.isLocked) {
                                Log.d(TAG, "global busy, rescheduling for $key")
                                // reprogramar esta misma runnable más tarde
                                mainHandler.postDelayed(runnable!!, debounceDelayMs)
                                return@launch
                            }
                            Log.d(TAG, "Debounced import start for series=$seriesId folder=$key")
                            globalMutex.withLock {
                                folderMutex.withLock {
                                    scanningSeries.value = scanningSeries.value + seriesId
                                    val dir = DocumentFile.fromTreeUri(context, folderUri) ?: return@withLock
                                    try {
                                        importRecursively(seriesId, dir)
                                    } finally {
                                        scanningSeries.value = scanningSeries.value - seriesId
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            pendingRunnables.remove(key)
                            Log.d(TAG, "Debounced import finished for series=$seriesId folder=$key")
                        }
                    }
                }

                pendingRunnables[key] = runnable
                mainHandler.postDelayed(runnable, debounceDelayMs)
            }
        }
        try {
            context.contentResolver.registerContentObserver(folderUri, true, observer)
            folderObservers[key] = observer
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopWatchingFolder(folderUri: Uri) {
        val key = folderUri.toString()
        val obs = folderObservers.remove(key) ?: return
        try {
            context.contentResolver.unregisterContentObserver(obs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun unbindFolderFromSeries(seriesId: Long) {
        try {
            val series = seriesDao.getSeriesById(seriesId).first() ?: return
            series.folderUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    stopWatchingFolder(uri)
                } catch (e: Exception) { e.printStackTrace() }
            }
            seriesDao.updateSeries(series.copy(folderUri = null))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveImageLocally(uri: Uri, fileName: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun bulkImportFromFolder(seriesId: Long, folderUri: Uri) {
        takePersistableUriPermission(folderUri)
        val directory = DocumentFile.fromTreeUri(context, folderUri) ?: return
        val key = folderUri.toString()
        val folderMutex = folderMutexes.getOrPut(key) { Mutex() }
        globalMutex.withLock {
            folderMutex.withLock {
                scanningSeries.value = scanningSeries.value + seriesId
                try {
                    importRecursively(seriesId, directory)
                } finally {
                    scanningSeries.value = scanningSeries.value - seriesId
                }
            }
        }
    }

    /**
     * Vincula una carpeta padre y crea/actualiza series por cada subcarpeta.
     * Por cada subcarpeta se creará (o se actualizará) una Series con folderUri apuntando a la subcarpeta
     * y se importarán los PDFs dentro de esa subcarpeta.
     */
    suspend fun bindParentFolderAndCreateSeries(parentFolderUri: Uri) {
        takePersistableUriPermission(parentFolderUri)
        val parent = DocumentFile.fromTreeUri(context, parentFolderUri) ?: return

        parent.listFiles().forEach { child ->
            if (child.isDirectory) {
                val name = child.name ?: return@forEach
                try {
                    val existing = seriesDao.getSeriesByName(name)
                    if (existing != null) {
                        // actualizar folderUri si no coincide
                        if (existing.folderUri != child.uri.toString()) {
                            seriesDao.updateSeries(existing.copy(folderUri = child.uri.toString()))
                        }
                        // importar contenidos
                        importRecursively(existing.id, child)
                        startWatchingFolder(existing.id, child.uri)
                    } else {
                        val newId = insertSeries(Series(name = name, folderUri = child.uri.toString()))
                        val dir = child
                        importRecursively(newId, dir)
                        startWatchingFolder(newId, child.uri)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun rescanSeriesFolder(seriesId: Long) {
        val series = seriesDao.getSeriesById(seriesId).first() ?: return
        val folderUriStr = series.folderUri ?: return
        val key = folderUriStr
        try {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(folderUriStr)) ?: return
            val folderMutex = folderMutexes.getOrPut(key) { Mutex() }
            globalMutex.withLock {
                folderMutex.withLock {
                    scanningSeries.value = scanningSeries.value + seriesId
                    try {
                        importRecursively(seriesId, dir)
                    } finally {
                        scanningSeries.value = scanningSeries.value - seriesId
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun rescanAllBoundFolders() {
        val seriesList = seriesDao.getAllSeries().first()
        seriesList.forEach { series ->
            series.folderUri?.let { uriStr ->
                try {
                    val dir = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return@let
                    val key = uriStr
                    val folderMutex = folderMutexes.getOrPut(key) { Mutex() }
                    globalMutex.withLock {
                        folderMutex.withLock {
                            scanningSeries.value = scanningSeries.value + series.id
                            try {
                                importRecursively(series.id, dir)
                            } finally {
                                scanningSeries.value = scanningSeries.value - series.id
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun importRecursively(seriesId: Long, directory: DocumentFile) {
        Log.d(TAG, "importRecursively: start for series=$seriesId dir=${directory.uri}")
        var processed = 0
        val childDirectories = mutableListOf<DocumentFile>()
        val supportedFiles = mutableListOf<DocumentFile>()

        directory.listFiles()
            .sortedWith(compareBy<DocumentFile>({ it.name ?: "" }, { it.uri.toString() }))
            .forEach { file ->
                try {
                    Log.d(TAG, "scanning file: name=${file.name} uri=${file.uri} type=${file.type} isDir=${file.isDirectory}")
                } catch (_: Exception) {}
                when {
                    file.isDirectory -> childDirectories.add(file)
                    BookFormatUtils.isSupported(file.type, file.name) -> supportedFiles.add(file)
                }
            }

        // Ensure we have persistable permissions for the directory
        try { takePersistableUriPermission(directory.uri) } catch (_: Exception) {}

        childDirectories.forEach { child ->
            importRecursively(seriesId, child)
        }

        supportedFiles.forEach { file ->
            try {
                Log.d(TAG, "processing supported file: name=${file.name} uri=${file.uri} type=${file.type}")
            } catch (_: Exception) {}
            val book = createBookFromUri(
                seriesId = seriesId,
                uri = file.uri,
                mimeType = file.type,
                name = file.name
            )
            val inserted = if (book != null) insertBookIfMissing(book) else null
            if (inserted != null) {
                processed++
                try { Log.d(TAG, "inserted book for file: ${file.name} -> id=$inserted") } catch (_: Exception) {}
            }
        }

        Log.d(TAG, "importRecursively: finished for series=$seriesId dir=${directory.uri} processed=$processed")
        try {
            val booksNow = bookDao.getBooksBySeries(seriesId).first().map { it.pdfUri }
            Log.d(TAG, "importRecursively: books now for series=$seriesId -> ${booksNow.size} items")
            booksNow.forEach { uri -> Log.d(TAG, "imported-book-uri: $uri") }
        } catch (_: Exception) {}
    }

    private suspend fun createBookFromUri(
        seriesId: Long,
        uri: Uri,
        mimeType: String?,
        name: String?
    ): Book? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        if (bookDao.getBookBySeriesAndPdfUri(seriesId, uriString) != null) return@withContext null

        val isPdf = BookFormatUtils.isPdf(mimeType, name) || BookFormatUtils.isPdfUri(context, uri)
        return@withContext if (isPdf) {
            val thumbnailUri = PdfHelper.generateThumbnail(context, uri)
            var totalPages = 0
            try {
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists() && file.canRead()) {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                totalPages = renderer.pageCount
                            }
                        }
                    }
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            totalPages = renderer.pageCount
                        }
                    }
                }
            } catch (_: Exception) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val temp = File(context.cacheDir, "temp_count_${kotlin.math.abs(uriString.hashCode())}.pdf")
                        temp.outputStream().use { stream.copyTo(it) }
                        if (temp.exists() && temp.length() > 0) {
                            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                                PdfRenderer(pfd).use { renderer ->
                                    totalPages = renderer.pageCount
                                }
                            }
                            temp.delete()
                        }
                    }
                } catch (_: Exception) {}
            }
            Book(
                seriesId = seriesId,
                pdfUri = uriString,
                coverUri = thumbnailUri,
                totalPages = totalPages,
                lastReadTimestamp = 0
            )
        } else {
            null
        }
    }


    private fun stableHash(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            digest.update(part.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(24)
    }


    // --- Notebook Sharing ---

    suspend fun exportSeries(seriesId: Long): Uri? = withContext(Dispatchers.IO) {
        val series = seriesDao.getSeriesById(seriesId).first() ?: return@withContext null
        val books = bookDao.getBooksBySeries(seriesId).first()

        val exportDir = File(context.cacheDir, "export_${UUID.randomUUID()}")
        exportDir.mkdirs()

        val booksMetadata = mutableListOf<BookExportMetadata>()
        for (book in books) {
            val pdfFile = File(exportDir, "book_${book.id}.pdf")
            val coverFile = if (book.coverUri != null) File(exportDir, "cover_${book.id}.png") else null

            try {
                // Copy PDF
                context.contentResolver.openInputStream(Uri.parse(book.pdfUri))?.use { input ->
                    pdfFile.outputStream().use { output -> input.copyTo(output) }
                }
                
                // Copy Cover if exists
                book.coverUri?.let { coverUriStr ->
                    context.contentResolver.openInputStream(Uri.parse(coverUriStr))?.use { input ->
                        coverFile?.outputStream()?.use { output -> input.copyTo(output) }
                    }
                }

                booksMetadata.add(
                    BookExportMetadata(
                        fileName = Uri.parse(book.pdfUri).lastPathSegment ?: "Book",
                        customOrder = book.customOrder,
                        totalPages = book.totalPages,
                        pdfFileName = pdfFile.name,
                        coverFileName = coverFile?.name
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val metadata = SeriesExportMetadata(name = series.name, books = booksMetadata)
        val metadataFile = File(exportDir, "metadata.json")
        val adapter = moshi.adapter(SeriesExportMetadata::class.java)
        metadataFile.writeText(adapter.toJson(metadata))

        val zipFile = File(context.cacheDir, "shared/${series.name.replace(" ", "_")}.easyreader")
        zipFile.parentFile?.mkdirs()
        ZipHelper.zip(exportDir.listFiles()?.toList() ?: emptyList(), zipFile)
        
        exportDir.deleteRecursively()

        FileProvider.getUriForFile(context, "com.example.easyreader.fileprovider", zipFile)
    }

    suspend fun importSeries(packageUri: Uri) = withContext(Dispatchers.IO) {
        val importDir = File(context.cacheDir, "import_${UUID.randomUUID()}")
        importDir.mkdirs()

        try {
            val tempZip = File(importDir, "package.zip")
            context.contentResolver.openInputStream(packageUri)?.use { input ->
                tempZip.outputStream().use { output -> input.copyTo(output) }
            }

            val extractDir = File(importDir, "extracted")
            extractDir.mkdirs()
            ZipHelper.unzip(tempZip, extractDir)

            val metadataFile = File(extractDir, "metadata.json")
            if (!metadataFile.exists()) return@withContext

            val adapter = moshi.adapter(SeriesExportMetadata::class.java)
            val metadata = adapter.fromJson(metadataFile.readText()) ?: return@withContext

            val newSeriesId = seriesDao.insertSeries(Series(name = metadata.name))

            for (bookMeta in metadata.books) {
                val pdfFile = File(extractDir, bookMeta.pdfFileName)
                if (!pdfFile.exists()) continue

                val internalPdf = File(context.filesDir, "imported_${UUID.randomUUID()}.pdf")
                pdfFile.inputStream().use { input ->
                    internalPdf.outputStream().use { output -> input.copyTo(output) }
                }

                var internalCover: String? = null
                bookMeta.coverFileName?.let { coverName ->
                    val coverFile = File(extractDir, coverName)
                    if (coverFile.exists()) {
                        val newCover = File(context.filesDir, "imported_cover_${UUID.randomUUID()}.png")
                        coverFile.inputStream().use { input ->
                            newCover.outputStream().use { output -> input.copyTo(output) }
                        }
                        internalCover = Uri.fromFile(newCover).toString()
                    }
                }

                bookDao.insertBook(
                    Book(
                        seriesId = newSeriesId,
                        pdfUri = Uri.fromFile(internalPdf).toString(),
                        coverUri = internalCover,
                        totalPages = bookMeta.totalPages,
                        customOrder = bookMeta.customOrder
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            importDir.deleteRecursively()
        }
    }

    // --- Backup & Restore ---

    suspend fun backupLibrary(): Uri? = withContext(Dispatchers.IO) {
        val backupDir = File(context.cacheDir, "backup_${UUID.randomUUID()}")
        backupDir.mkdirs()

        // Room DB files
        val dbFile = context.getDatabasePath("easy_reader_database")
        val shmFile = File(dbFile.path + "-shm")
        val walFile = File(dbFile.path + "-wal")

        val filesToBackup = mutableListOf<File>()
        if (dbFile.exists()) filesToBackup.add(dbFile)
        if (shmFile.exists()) filesToBackup.add(shmFile)
        if (walFile.exists()) filesToBackup.add(walFile)
        
        // Add filesDir (contains custom covers and imported PDFs)
        val filesDir = context.filesDir
        if (filesDir.exists()) filesToBackup.add(filesDir)

        val zipFile = File(context.cacheDir, "shared/EasyReader_Backup_${System.currentTimeMillis()}.zip")
        zipFile.parentFile?.mkdirs()
        
        // We need to be careful about zipping live DB. Close it before backup.
        try {
            EasyReaderDatabase.closeDatabase()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        ZipHelper.zip(filesToBackup, zipFile)

        FileProvider.getUriForFile(context, "com.example.easyreader.fileprovider", zipFile)
    }

    suspend fun restoreLibrary(backupUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val restoreDir = File(context.cacheDir, "restore_${UUID.randomUUID()}")
        restoreDir.mkdirs()

        try {
            val tempZip = File(restoreDir, "backup.zip")
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                tempZip.outputStream().use { output -> input.copyTo(output) }
            }

            val extractDir = File(restoreDir, "extracted")
            extractDir.mkdirs()
            ZipHelper.unzip(tempZip, extractDir)

            // Close DB before overwriting
            EasyReaderDatabase.closeDatabase()

            // Replace DB files
            val dbFile = context.getDatabasePath("easy_reader_database")
            val shmFile = File(dbFile.path + "-shm")
            val walFile = File(dbFile.path + "-wal")

            // Remove existing SQLite temporary journal files to prevent corruption
            if (shmFile.exists()) shmFile.delete()
            if (walFile.exists()) walFile.delete()

            File(extractDir, "easy_reader_database").let { if (it.exists()) it.copyTo(dbFile, overwrite = true) }
            File(extractDir, "easy_reader_database-shm").let { if (it.exists()) it.copyTo(shmFile, overwrite = true) }
            File(extractDir, "easy_reader_database-wal").let { if (it.exists()) it.copyTo(walFile, overwrite = true) }

            // Replace filesDir
            val extractedFilesDir = File(extractDir, "files")
            if (extractedFilesDir.exists()) {
                context.filesDir.deleteRecursively()
                context.filesDir.mkdirs()
                extractedFilesDir.copyRecursively(context.filesDir, overwrite = true)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            restoreDir.deleteRecursively()
        }
    }
}
