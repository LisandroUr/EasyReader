package com.example.easyreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.easyreader.data.Book
import com.example.easyreader.data.Series

enum class LibraryTab {
    SERIES,
    ALL_BOOKS,
    RECENT
}

enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    BOOK_COUNT,
    RECENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LibraryViewModel?,
    onSeriesClick: (Long) -> Unit,
    onBookClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val allSeries by viewModel?.allSeries?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val allBooks by viewModel?.allBooks?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val recentlyRead by viewModel?.recentlyReadBooks?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val seriesBookCounts by viewModel?.seriesBookCounts?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyMap()) }
    val searchQuery by viewModel?.searchQuery?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") }
    val bookSearchResults by viewModel?.bookSearchResults?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val seriesSearchResults by viewModel?.seriesSearchResults?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val isWorking by viewModel?.isWorking?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val workingMessage by viewModel?.workingMessage?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableStateOf(LibraryTab.SERIES) }
    var isGridView by remember { mutableStateOf(true) }
    var sortOption by remember { mutableStateOf(SortOption.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    var showAddSheet by remember { mutableStateOf(false) }
    var showCreateSeriesDialog by remember { mutableStateOf(false) }
    var seriesToEdit by remember { mutableStateOf<Series?>(null) }
    var seriesForCover by remember { mutableStateOf<Series?>(null) }

    // Cover picker
    val pickCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            seriesForCover?.let { s -> viewModel?.updateSeriesCover(s, it) }
        }
        seriesForCover = null
    }

    // Master folder picker (creates series per subfolder)
    val pickMasterFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel?.bindParentFolderAndCreateSeries(it) }
    }

    // Processed Series list with sorting
    val processedSeries = remember(allSeries, seriesBookCounts, sortOption) {
        when (sortOption) {
            SortOption.NAME_ASC -> allSeries.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> allSeries.sortedByDescending { it.name.lowercase() }
            SortOption.BOOK_COUNT -> allSeries.sortedByDescending { seriesBookCounts[it.id] ?: 0 }
            SortOption.RECENT -> allSeries.sortedByDescending { it.id }
        }
    }

    val isSearching = searchQuery.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "EasyReader",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel?.rescanAllBoundFolders() },
                            enabled = !isWorking
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Sincronizar carpetas ahora")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Ajustes de la biblioteca")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel?.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = {
                        Text(
                            "Buscar en la biblioteca...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel?.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                // Navigation Tabs (when not searching)
                if (!isSearching) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == LibraryTab.SERIES,
                            onClick = { selectedTab = LibraryTab.SERIES },
                            text = { Text("Series (${allSeries.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == LibraryTab.ALL_BOOKS,
                            onClick = { selectedTab = LibraryTab.ALL_BOOKS },
                            text = { Text("Libros (${allBooks.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == LibraryTab.RECENT,
                            onClick = { selectedTab = LibraryTab.RECENT },
                            text = { Text("Recientes (${recentlyRead.size})", fontWeight = FontWeight.Bold) }
                        )
                    }

                    // Toolbar (Sort & View Mode)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box {
                            TextButton(
                                onClick = { showSortMenu = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (sortOption) {
                                        SortOption.NAME_ASC -> "Nombre (A-Z)"
                                        SortOption.NAME_DESC -> "Nombre (Z-A)"
                                        SortOption.BOOK_COUNT -> "Más tomos"
                                        SortOption.RECENT -> "Más recientes"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Nombre (A-Z)") },
                                    onClick = { sortOption = SortOption.NAME_ASC; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nombre (Z-A)") },
                                    onClick = { sortOption = SortOption.NAME_DESC; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Mayor cantidad de tomos") },
                                    onClick = { sortOption = SortOption.BOOK_COUNT; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Agregados recientemente") },
                                    onClick = { sortOption = SortOption.RECENT; showSortMenu = false }
                                )
                            }
                        }

                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Cambiar modo de vista",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir a la biblioteca")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Active background work indicator
            AnimatedVisibility(
                visible = isWorking,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = workingMessage ?: "Sincronizando biblioteca...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (isSearching) {
                SearchResultsView(
                    seriesResults = seriesSearchResults,
                    bookResults = bookSearchResults,
                    seriesBookCounts = seriesBookCounts,
                    onSeriesClick = onSeriesClick,
                    onBookClick = onBookClick,
                    onEditSeries = { seriesToEdit = it },
                    onChangeSeriesCover = {
                        seriesForCover = it
                        pickCoverLauncher.launch("image/*")
                    },
                    onDeleteSeries = { viewModel?.deleteSeries(it) }
                )
            } else {
                when (selectedTab) {
                    LibraryTab.SERIES -> {
                        SeriesTabView(
                            seriesList = processedSeries,
                            bookCounts = seriesBookCounts,
                            isGridView = isGridView,
                            onSeriesClick = onSeriesClick,
                            onEditSeries = { seriesToEdit = it },
                            onChangeCover = {
                                seriesForCover = it
                                pickCoverLauncher.launch("image/*")
                            },
                            onDeleteSeries = { viewModel?.deleteSeries(it) },
                            onEmptyAction = { showAddSheet = true }
                        )
                    }
                    LibraryTab.ALL_BOOKS -> {
                        AllBooksTabView(
                            books = allBooks,
                            isGridView = isGridView,
                            onBookClick = onBookClick
                        )
                    }
                    LibraryTab.RECENT -> {
                        RecentlyReadTabView(
                            recentBooks = recentlyRead,
                            onBookClick = onBookClick
                        )
                    }
                }
            }
        }

        // Add Options BottomSheet
        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Añadir a la Biblioteca",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ListItem(
                        headlineContent = { Text("Nueva serie vacía", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Crea una colección para organizar volúmenes manualmente", style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            FilledTonalIconButton(onClick = {
                                showAddSheet = false
                                showCreateSeriesDialog = true
                            }) {
                                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                showAddSheet = false
                                showCreateSeriesDialog = true
                            }
                    )

                    ListItem(
                        headlineContent = { Text("Vincular carpeta local", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Sincroniza automáticamente los PDFs de una carpeta del dispositivo", style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            FilledTonalIconButton(onClick = {
                                showAddSheet = false
                                showCreateSeriesDialog = true
                            }) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                showAddSheet = false
                                showCreateSeriesDialog = true
                            }
                    )

                    ListItem(
                        headlineContent = { Text("Importar carpeta raíz", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Escanea una carpeta principal y genera una serie por cada subcarpeta", style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            FilledTonalIconButton(onClick = {
                                showAddSheet = false
                                pickMasterFolderLauncher.launch(null)
                            }) {
                                Icon(Icons.Outlined.DriveFolderUpload, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                showAddSheet = false
                                pickMasterFolderLauncher.launch(null)
                            }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Create / Edit Series Dialog
        if (showCreateSeriesDialog) {
            CreateSeriesDialog(
                onDismiss = { showCreateSeriesDialog = false },
                onConfirm = { name ->
                    viewModel?.addSeries(name)
                    showCreateSeriesDialog = false
                }
            )
        }

        seriesToEdit?.let { s ->
            EditSeriesDialog(
                currentName = s.name,
                onDismiss = { seriesToEdit = null },
                onConfirm = { newName ->
                    viewModel?.updateSeries(s.copy(name = newName))
                    seriesToEdit = null
                }
            )
        }
    }
}

// ---------------- Tabs & Views ----------------

@Composable
fun SeriesTabView(
    seriesList: List<Series>,
    bookCounts: Map<Long, Int>,
    isGridView: Boolean,
    onSeriesClick: (Long) -> Unit,
    onEditSeries: (Series) -> Unit,
    onChangeCover: (Series) -> Unit,
    onDeleteSeries: (Series) -> Unit,
    onEmptyAction: () -> Unit
) {
    if (seriesList.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.FolderSpecial,
            title = "Tu biblioteca está vacía",
            description = "Crea tu primera serie o vincula una carpeta con volúmenes PDF para comenzar.",
            actionText = "Crear serie",
            onAction = onEmptyAction
        )
    } else if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(seriesList, key = { it.id }) { series ->
                SeriesGridCard(
                    series = series,
                    bookCount = bookCounts[series.id] ?: 0,
                    onClick = { onSeriesClick(series.id) },
                    onEdit = { onEditSeries(series) },
                    onChangeCover = { onChangeCover(series) },
                    onDelete = { onDeleteSeries(series) }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(seriesList, key = { it.id }) { series ->
                SeriesListItem(
                    series = series,
                    bookCount = bookCounts[series.id] ?: 0,
                    onClick = { onSeriesClick(series.id) },
                    onEdit = { onEditSeries(series) },
                    onChangeCover = { onChangeCover(series) },
                    onDelete = { onDeleteSeries(series) }
                )
            }
        }
    }
}

@Composable
fun AllBooksTabView(
    books: List<Book>,
    isGridView: Boolean,
    onBookClick: (Long) -> Unit
) {
    if (books.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.PictureAsPdf,
            title = "Sin volúmenes disponibles",
            description = "Importa archivos PDF dentro de una serie para verlos organizados aquí.",
            actionText = null,
            onAction = null
        )
    } else if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books, key = { it.id }) { book ->
                BookGridCard(book = book, onClick = { onBookClick(book.id) })
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(books, key = { it.id }) { book ->
                BookListItem(book = book, onClick = { onBookClick(book.id) })
            }
        }
    }
}

@Composable
fun RecentlyReadTabView(
    recentBooks: List<Book>,
    onBookClick: (Long) -> Unit
) {
    if (recentBooks.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "Sin lecturas recientes",
            description = "Los cómics o volúmenes que abras mostrarán aquí tu avance exacto.",
            actionText = null,
            onAction = null
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recentBooks, key = { it.id }) { book ->
                RecentBookBigCard(book = book, onClick = { onBookClick(book.id) })
            }
        }
    }
}

@Composable
fun SearchResultsView(
    seriesResults: List<Series>,
    bookResults: List<Book>,
    seriesBookCounts: Map<Long, Int>,
    onSeriesClick: (Long) -> Unit,
    onBookClick: (Long) -> Unit,
    onEditSeries: (Series) -> Unit,
    onChangeSeriesCover: (Series) -> Unit,
    onDeleteSeries: (Series) -> Unit
) {
    if (seriesResults.isEmpty() && bookResults.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No se encontraron resultados",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (seriesResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Series (${seriesResults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(seriesResults, key = { "s_${it.id}" }) { series ->
                    SeriesListItem(
                        series = series,
                        bookCount = seriesBookCounts[series.id] ?: 0,
                        onClick = { onSeriesClick(series.id) },
                        onEdit = { onEditSeries(series) },
                        onChangeCover = { onChangeSeriesCover(series) },
                        onDelete = { onDeleteSeries(series) }
                    )
                }
            }

            if (bookResults.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Volúmenes (${bookResults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(bookResults, key = { "b_${it.id}" }) { book ->
                    BookListItem(book = book, onClick = { onBookClick(book.id) })
                }
            }
        }
    }
}

// ---------------- Card Components ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesGridCard(
    series: Series,
    bookCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = series.coverUri ?: "android.resource://com.example.easyreader/drawable/placeholder",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Top gradient scrim for visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                        )
                    )
            )

            // Badges row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "$bookCount ${if (bookCount == 1) "tomo" else "tomos"}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (series.folderUri != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Carpeta sincronizada",
                            modifier = Modifier
                                .size(18.dp)
                                .padding(2.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Bottom gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
            )

            // Series Name
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Editar nombre") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Cambiar portada") },
                    leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                    onClick = { showMenu = false; onChangeCover() }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Eliminar serie", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
fun SeriesListItem(
    series: Series,
    bookCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
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
            Card(
                modifier = Modifier
                    .size(width = 50.dp, height = 70.dp),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                AsyncImage(
                    model = series.coverUri ?: "android.resource://com.example.easyreader/drawable/placeholder",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$bookCount ${if (bookCount == 1) "tomo" else "tomos"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (series.folderUri != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sincronizada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar nombre") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Cambiar portada") },
                        leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                        onClick = { showMenu = false; onChangeCover() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Eliminar serie", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun BookGridCard(book: Book, onClick: () -> Unit) {
    val fileName = remember(book.pdfUri) {
        val uri = book.pdfUri.toUri()
        val raw = uri.lastPathSegment ?: book.pdfUri
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        decoded.substringAfterLast('/').substringAfterLast(':').ifBlank { "Volumen" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AsyncImage(
                    model = book.coverUri ?: "android.resource://com.example.easyreader/drawable/placeholder",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.totalPages > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val progress = ((book.currentPage + 1).toFloat() / book.totalPages.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BookListItem(book: Book, onClick: () -> Unit) {
    val fileName = remember(book.pdfUri) {
        val uri = book.pdfUri.toUri()
        val raw = uri.lastPathSegment ?: book.pdfUri
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        decoded.substringAfterLast('/').substringAfterLast(':').ifBlank { "Volumen" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
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
            Card(
                modifier = Modifier.size(width = 46.dp, height = 64.dp),
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
                            text = "$percent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Documento PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RecentBookBigCard(book: Book, onClick: () -> Unit) {
    val fileName = remember(book.pdfUri) {
        val uri = book.pdfUri.toUri()
        val raw = uri.lastPathSegment ?: book.pdfUri
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        decoded.substringAfterLast('/').substringAfterLast(':').ifBlank { "Volumen" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(width = 68.dp, height = 96.dp),
                shape = MaterialTheme.shapes.small
            ) {
                AsyncImage(
                    model = book.coverUri ?: "android.resource://com.example.easyreader/drawable/placeholder",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (book.totalPages > 0) {
                    val progress = ((book.currentPage + 1).toFloat() / book.totalPages.toFloat()).coerceIn(0f, 1f)
                    val percent = (progress * 100).toInt()
                    Text(
                        text = "Pág. ${book.currentPage + 1} de ${book.totalPages} ($percent%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Continuar lectura", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ---------------- Dialogs & Empty State ----------------

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionText: String?,
    onAction: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAction,
                shape = MaterialTheme.shapes.small
            ) {
                Text(actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun CreateSeriesDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Nueva serie",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre de la serie") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Crear serie")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun EditSeriesDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Editar serie",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre de la serie") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Guardar cambios")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
