package com.example.easyreader.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.easyreader.data.Book
import com.example.easyreader.data.Series

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    viewModel: LibraryViewModel?,
    onBackClick: () -> Unit,
    onBookClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val seriesFlow = remember(seriesId, viewModel) { viewModel?.getSeriesById(seriesId) }
    val series by seriesFlow?.collectAsStateWithLifecycle(initialValue = null) ?: remember { mutableStateOf(null) }

    val booksFlow = remember(seriesId, viewModel) { viewModel?.getBooksBySeries(seriesId) }
    val books by booksFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val isWorking by viewModel?.isWorking?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val workingMessage by viewModel?.workingMessage?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val scanningSet by viewModel?.scanningSeries?.collectAsStateWithLifecycle(initialValue = emptySet()) ?: remember { mutableStateOf(emptySet()) }
    val isScanning = remember(scanningSet) { scanningSet.contains(seriesId) }

    var bookSearchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }

    var bookForCover by remember { mutableStateOf<Book?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showAddBooksSheet by remember { mutableStateOf(false) }
    var showDuplicateCleanupDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }

    // Cover pickers
    val pickBookCoverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { bookForCover?.let { b -> viewModel?.updateBookCover(b, it) } }
        bookForCover = null
    }

    val pickSeriesCoverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { series?.let { s -> viewModel?.updateSeriesCover(s, it) } }
    }

    // PDF Import pickers
    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel?.importBooks(seriesId, uris)
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { viewModel?.bindFolder(seriesId, it) }
    }

    val filteredBooks = remember(books, bookSearchQuery) {
        if (bookSearchQuery.isBlank()) {
            books
        } else {
            books.filter { b ->
                val name = b.pdfUri.toUri().lastPathSegment ?: ""
                name.contains(bookSearchQuery, ignoreCase = true)
            }
        }
    }

    val totalPagesInSeries = remember(books) { books.sumOf { it.totalPages } }
    val duplicateBooksCount = remember(books) {
        books.groupBy { it.pdfUri }.values.sumOf { group -> (group.size - 1).coerceAtLeast(0) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = bookSearchQuery,
                            onValueChange = { bookSearchQuery = it },
                            placeholder = { Text("Buscar tomo...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            trailingIcon = {
                                IconButton(onClick = {
                                    bookSearchQuery = ""
                                    isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cerrar búsqueda")
                                }
                            }
                        )
                    } else {
                        Text(
                            text = series?.name ?: "Detalle de serie",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar tomos")
                        }
                        IconButton(onClick = { isReorderMode = !isReorderMode }) {
                            Icon(
                                imageVector = if (isReorderMode) Icons.Default.Check else Icons.Default.SwapVert,
                                contentDescription = if (isReorderMode) "Listo" else "Reordenar tomos",
                                tint = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                    }

                    DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Editar nombre") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { showTopMenu = false; showEditNameDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Cambiar portada") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                            onClick = { showTopMenu = false; pickSeriesCoverLauncher.launch("image/*") }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar paquete (.easyreader)") },
                            leadingIcon = { Icon(Icons.Outlined.IosShare, null) },
                            onClick = {
                                showTopMenu = false
                                viewModel?.exportSeries(seriesId) { uri ->
                                    uri?.let {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_STREAM, it)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Compartir serie"))
                                    }
                                }
                            }
                        )
                        if (duplicateBooksCount > 0) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Limpiar duplicados ($duplicateBooksCount)") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                                onClick = { showTopMenu = false; showDuplicateCleanupDialog = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBooksSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir tomos")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Header Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    // Blurred Backdrop
                    AsyncImage(
                        model = series?.coverUri ?: "android.resource://com.example.easyreader/drawable/placeholder",
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(32.dp),
                        contentScale = ContentScale.Crop,
                        alpha = 0.35f
                    )

                    // Scrim Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )

                    // Header Content
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Card(
                            modifier = Modifier
                                .size(width = 104.dp, height = 144.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { pickSeriesCoverLauncher.launch("image/*") },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            AsyncImage(
                                model = series?.coverUri ?: "android.resource://com.example.easyreader/drawable/placeholder",
                                contentDescription = "Portada de la serie",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = series?.name ?: "",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("${books.size} tomos", style = MaterialTheme.typography.labelMedium) },
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                                if (totalPagesInSeries > 0) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("$totalPagesInSeries págs", style = MaterialTheme.typography.labelMedium) },
                                        shape = MaterialTheme.shapes.extraSmall
                                    )
                                }
                            }

                            if (series?.folderUri != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel?.unbindFolder(seriesId) }
                                ) {
                                    Icon(
                                        Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Carpeta vinculada (Desvincular)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status indicator banner
            if (isWorking || isScanning) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = workingMessage ?: "Escaneando carpeta vinculada...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Reorder Mode Hint
            if (isReorderMode) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reordenando tomos",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(onClick = { isReorderMode = false }) {
                                Text("Guardar orden", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Books List
            if (filteredBooks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (bookSearchQuery.isNotBlank()) "No se encontraron tomos para '$bookSearchQuery'" else "No hay tomos en esta serie",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (bookSearchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showAddBooksSheet = true },
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Añadir tomos PDF", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(filteredBooks, key = { _, b -> b.id }) { index, book ->
                    SeriesBookRow(
                        book = book,
                        index = index,
                        totalBooks = filteredBooks.size,
                        isReorderMode = isReorderMode,
                        onClick = { onBookClick(book.id) },
                        onMoveUp = if (index > 0) ({ viewModel?.moveBook(seriesId, book, true) }) else null,
                        onMoveDown = if (index < filteredBooks.size - 1) ({ viewModel?.moveBook(seriesId, book, false) }) else null,
                        onResetProgress = { viewModel?.resetBookProgress(book) },
                        onChangeCover = {
                            bookForCover = book
                            pickBookCoverLauncher.launch("image/*")
                        },
                        onDelete = { viewModel?.deleteBook(book) }
                    )
                }
            }
        }

        // Add Books ModalBottomSheet
        if (showAddBooksSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddBooksSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Añadir Tomos a la Serie",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ListItem(
                        headlineContent = { Text("Seleccionar archivos PDF", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Elige uno o varios tomos PDF en tu almacenamiento", style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            FilledTonalIconButton(onClick = {
                                showAddBooksSheet = false
                                pickPdfLauncher.launch(arrayOf("application/pdf"))
                            }) {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                showAddBooksSheet = false
                                pickPdfLauncher.launch(arrayOf("application/pdf"))
                            }
                    )

                    ListItem(
                        headlineContent = { Text("Vincular carpeta de tomos", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Importa todos los PDFs de una carpeta local y mantén el contenido al día", style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            FilledTonalIconButton(onClick = {
                                showAddBooksSheet = false
                                pickFolderLauncher.launch(null)
                            }) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                showAddBooksSheet = false
                                pickFolderLauncher.launch(null)
                            }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Edit Series Name Dialog
        if (showEditNameDialog && series != null) {
            EditSeriesDialog(
                currentName = series!!.name,
                onDismiss = { showEditNameDialog = false },
                onConfirm = { newName ->
                    viewModel?.updateSeries(series!!.copy(name = newName))
                    showEditNameDialog = false
                }
            )
        }

        // Duplicate Cleanup Dialog
        if (showDuplicateCleanupDialog) {
            AlertDialog(
                onDismissRequest = { showDuplicateCleanupDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        "Limpiar duplicados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        "Se encontraron $duplicateBooksCount tomos duplicados. ¿Deseas conservar las versiones únicas y eliminar las copias repetidas?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel?.removeDuplicateBooks(seriesId)
                            showDuplicateCleanupDialog = false
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Eliminar duplicados")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDuplicateCleanupDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun SeriesBookRow(
    book: Book,
    index: Int,
    totalBooks: Int,
    isReorderMode: Boolean,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onResetProgress: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val fileName = remember(book.pdfUri) {
        val uri = book.pdfUri.toUri()
        val raw = uri.lastPathSegment ?: book.pdfUri
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        decoded.substringAfterLast('/').substringAfterLast(':').ifBlank { "Tomo ${index + 1}" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book cover
            Card(
                modifier = Modifier.size(width = 48.dp, height = 68.dp),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                AsyncImage(
                    model = book.coverUri ?: "android.resource://com.example.easyreader/drawable/placeholder",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Book Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (book.totalPages > 0) {
                    val progress = ((book.currentPage + 1).toFloat() / book.totalPages.toFloat()).coerceIn(0f, 1f)
                    val percent = (progress * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${book.currentPage + 1}/${book.totalPages} ($percent%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Sin comenzar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions or Reorder Controls
            if (isReorderMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onMoveUp?.invoke() },
                        enabled = onMoveUp != null
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Subir en el orden")
                    }
                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = onMoveDown != null
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar en el orden")
                    }
                }
            } else {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Cambiar portada") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                            onClick = { showMenu = false; onChangeCover() }
                        )
                        DropdownMenuItem(
                            text = { Text("Reiniciar progreso") },
                            leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                            onClick = { showMenu = false; onResetProgress() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Eliminar de la serie", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
