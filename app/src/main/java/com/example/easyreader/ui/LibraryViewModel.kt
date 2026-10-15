package com.example.easyreader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.example.easyreader.data.Book
import com.example.easyreader.data.LibraryRepository
import com.example.easyreader.data.Series
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.easyreader.worker.LibrarySyncWorker
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application)
    private val appPreferences = application.getSharedPreferences("easyreader_prefs", Application.MODE_PRIVATE)
    private val _isWorking = MutableStateFlow(false)
    val isWorking = _isWorking.asStateFlow()
    private val _workingMessage = MutableStateFlow<String?>(null)
    val workingMessage = _workingMessage.asStateFlow()
    private val cleanupAppliedKey = "pdf_only_cleanup_applied"

    private val _syncInterval = MutableStateFlow(appPreferences.getInt("sync_interval_hours", 6))
    val syncInterval = _syncInterval.asStateFlow()

    fun setSyncInterval(hours: Int) {
        appPreferences.edit().putInt("sync_interval_hours", hours).apply()
        _syncInterval.value = hours
        scheduleLibrarySyncWork(hours)
    }

    private fun scheduleLibrarySyncWork(hours: Int) {
        val workManager = WorkManager.getInstance(getApplication())
        if (hours == 0) {
            workManager.cancelUniqueWork("library_sync_work")
        } else {
            val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(hours.toLong(), TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(
                "library_sync_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    init {
        // Inicializar el worker con la preferencia actual
        scheduleLibrarySyncWork(_syncInterval.value)

        if (!appPreferences.getBoolean(cleanupAppliedKey, false)) {
            // Limpieza automática: eliminar cualquier libro antiguo que no sea PDF.
            viewModelScope.launch {
                _isWorking.value = true
                _workingMessage.value = "Limpiando biblioteca antigua..."
                try {
                    repository.removeNonPdfBooksFromLibrary()
                    repository.removeEmptySeriesFromLibrary()
                    appPreferences.edit().putBoolean(cleanupAppliedKey, true).apply()
                } catch (_: Exception) {
                } finally {
                    _workingMessage.value = null
                    _isWorking.value = false
                }
            }
        }

        // Arrancar watchers para series que ya tienen carpeta vinculada
        viewModelScope.launch {
            repository.getAllSeries().collect { list ->
                list.forEach { series ->
                    series.folderUri?.let { uriStr ->
                        try {
                            val uri = uriStr.toUri()
                            repository.startWatchingFolder(series.id, uri)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        // Re-scan periódico mientras la app esté en memoria (fallback cuando ContentObserver no notifique)
        viewModelScope.launch {
            while (true) {
                try {
                    repository.rescanAllBoundFolders()
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(4 * 60 * 60 * 1000L) // 4 horas
            }
        }
    }

    fun bindParentFolderAndCreateSeries(parentFolderUri: Uri) {
        viewModelScope.launch {
            _isWorking.value = true
            _workingMessage.value = "Vinculando carpeta y creando series..."
            try {
                repository.bindParentFolderAndCreateSeries(parentFolderUri)
            } finally {
                _workingMessage.value = null
                _isWorking.value = false
            }
        }
    }

    val allSeries: StateFlow<List<Series>> = repository.getAllSeries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentlyReadBooks: StateFlow<List<Book>> = repository.getRecentlyReadBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBooks: StateFlow<List<Book>> = repository.getAllBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val seriesBookCounts: StateFlow<Map<Long, Int>> = allBooks
        .map { books -> books.groupBy { it.seriesId }.mapValues { it.value.size } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    private val _themeMode = MutableStateFlow(appPreferences.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        appPreferences.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    private val _dynamicColor = MutableStateFlow(appPreferences.getBoolean("dynamic_color", true))
    val dynamicColor = _dynamicColor.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        appPreferences.edit().putBoolean("dynamic_color", enabled).apply()
        _dynamicColor.value = enabled
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val bookSearchResults: StateFlow<List<Book>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else repository.searchBooks(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val seriesSearchResults: StateFlow<List<Series>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else repository.searchSeries(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Series que están siendo escaneadas actualmente (ids)
    val scanningSeries: StateFlow<Set<Long>> = repository.getScanningSeries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    private val booksFlows = mutableMapOf<Long, StateFlow<List<Book>>>()
    private val seriesFlows = mutableMapOf<Long, Flow<Series?>>()

    fun getSeriesById(id: Long): Flow<Series?> {
        return seriesFlows.getOrPut(id) {
            repository.getSeriesById(id)
        }
    }

    fun getBooksBySeries(seriesId: Long): StateFlow<List<Book>> {
        return booksFlows.getOrPut(seriesId) {
            repository.getBooksBySeries(seriesId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
                )
        }
    }

    fun addSeries(name: String) {
        viewModelScope.launch {
            repository.insertSeries(Series(name = name))
        }
    }

    fun updateSeries(series: Series) {
        viewModelScope.launch {
            repository.updateSeries(series)
        }
    }

    fun deleteSeries(series: Series) {
        viewModelScope.launch {
            repository.deleteSeries(series)
        }
    }

    fun removeDuplicateBooks(seriesId: Long) {
        viewModelScope.launch {
            _isWorking.value = true
            _workingMessage.value = "Eliminando duplicados..."
            try {
                repository.removeDuplicateBooksFromSeries(seriesId)
            } finally {
                _workingMessage.value = null
                _isWorking.value = false
            }
        }
    }

    fun cleanNonPdfBooksFromLibrary() {
        viewModelScope.launch {
            _isWorking.value = true
            _workingMessage.value = "Limpiando biblioteca antigua..."
            try {
                repository.removeNonPdfBooksFromLibrary()
                repository.removeEmptySeriesFromLibrary()
            } finally {
                _workingMessage.value = null
                _isWorking.value = false
            }
        }
    }

    private suspend fun importBookInternal(seriesId: Long, pdfUri: Uri) {
        repository.takePersistableUriPermission(pdfUri)
        repository.importUriIfSupported(seriesId, pdfUri)
    }

    fun importBook(seriesId: Long, pdfUri: Uri) {
        viewModelScope.launch {
            _isWorking.value = true
            _workingMessage.value = "Importando archivo..."
            try {
                importBookInternal(seriesId, pdfUri)
            } finally {
                _workingMessage.value = null
                _isWorking.value = false
            }
        }
    }

    fun importBooks(seriesId: Long, pdfUris: List<Uri>) {
        viewModelScope.launch {
            _isWorking.value = true
            _workingMessage.value = "Importando ${pdfUris.size} archivos..."
            try {
                pdfUris.forEach { uri -> importBookInternal(seriesId, uri) }
            } finally {
                _workingMessage.value = null
                _isWorking.value = false
            }
        }
    }

    fun bulkImport(seriesId: Long, folderUri: Uri) {
        viewModelScope.launch {
            _isWorking.value = true
            _workingMessage.value = "Importando carpeta..."
            try {
                repository.bulkImportFromFolder(seriesId, folderUri)
            } finally {
                _workingMessage.value = null
                _isWorking.value = false
            }
        }
    }

    fun bindFolder(seriesId: Long, folderUri: Uri) {
        viewModelScope.launch {
            _isWorking.value = true
            _workingMessage.value = "Vinculando carpeta..."
            try {
                repository.bindFolderToSeries(seriesId, folderUri)
                repository.startWatchingFolder(seriesId, folderUri)
            } finally {
                _workingMessage.value = null
                _isWorking.value = false
            }
        }
    }

    fun rescanAllBoundFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.rescanAllBoundFolders()
        }
    }

    fun unbindFolder(seriesId: Long) {
        viewModelScope.launch {
            repository.unbindFolderFromSeries(seriesId)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    fun updateBookProgress(book: Book, page: Int, forceUpdateTimestamp: Boolean = false) {
        viewModelScope.launch {
            if (book.currentPage != page || forceUpdateTimestamp) {
                repository.updateBook(book.copy(
                    currentPage = page,
                    lastReadTimestamp = System.currentTimeMillis()
                ))
            }
        }
    }

    fun resetBookProgress(book: Book) {
        viewModelScope.launch {
            repository.updateBook(book.copy(
                currentPage = 0,
                lastReadTimestamp = 0
            ))
        }
    }

    fun setReadingBookmark(book: Book, page: Int) {
        viewModelScope.launch {
            repository.updateBook(book.copy(bookmarkPage = page))
        }
    }

    fun clearReadingBookmark(book: Book) {
        viewModelScope.launch {
            repository.updateBook(book.copy(bookmarkPage = -1))
        }
    }

    fun getBookById(id: Long): Flow<Book?> = repository.getBookById(id)

    fun moveBook(seriesId: Long, book: Book, up: Boolean) {
        viewModelScope.launch {
            val books = repository.getBooksBySeries(seriesId).first()
            val index = books.indexOfFirst { it.id == book.id }
            if (index == -1) return@launch

            val targetIndex = if (up) index - 1 else index + 1
            if (targetIndex in books.indices) {
                val otherBook = books[targetIndex]
                val currentOrder = book.customOrder
                val targetOrder = otherBook.customOrder

                repository.updateBook(book.copy(customOrder = targetOrder))
                repository.updateBook(otherBook.copy(customOrder = currentOrder))
            }
        }
    }

    fun updateSeriesCover(series: Series, coverUri: Uri) {
        viewModelScope.launch {
            val localUri = repository.saveImageLocally(coverUri, "series_${series.id}_${System.currentTimeMillis()}.jpg")
            if (localUri != null) {
                repository.updateSeries(series.copy(coverUri = localUri))
            }
        }
    }

    fun updateBookCover(book: Book, coverUri: Uri) {
        viewModelScope.launch {
            val localUri = repository.saveImageLocally(coverUri, "book_${book.id}_${System.currentTimeMillis()}.jpg")
            if (localUri != null) {
                repository.updateBook(book.copy(coverUri = localUri))
            }
        }
    }

    // --- Sharing & Backup ---

    fun exportSeries(seriesId: Long, onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val uri = repository.exportSeries(seriesId)
            onResult(uri)
        }
    }

    fun importSeries(packageUri: Uri) {
        viewModelScope.launch {
            repository.importSeries(packageUri)
        }
    }

    fun backupLibrary(onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val uri = repository.backupLibrary()
            onResult(uri)
        }
    }

    fun restoreLibrary(backupUri: Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isWorking.value = true
            _workingMessage.value = "Restaurando biblioteca..."
            val success = try {
                repository.restoreLibrary(backupUri)
            } catch (_: Exception) {
                false
            } finally {
                _workingMessage.value = null
                _isWorking.value = false
            }
            onResult(success)
        }
    }
}
