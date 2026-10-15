package com.example.easyreader.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.easyreader.data.Book
import com.example.easyreader.util.BookFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ReaderThemeMode {
    NORMAL,
    SEPIA,
    NIGHT
}

enum class ScrollMode {
    PAGED_HORIZONTAL,
    CONTINUOUS_VERTICAL
}

/**
 * Punto de conexión con NavHost (`MainActivity.kt`) y `LibraryViewModel.kt`.
 * Lector de PDFs integrado usando `PdfRenderer` nativo de Android.
 * Soporta desplazamiento vertical continuo en LazyColumn o paginado horizontal en HorizontalPager.
 * No desconectar ni modificar rutas sin revisar `docs/CONNECTIONS.md`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    bookId: Long,
    viewModel: LibraryViewModel,
    onBackClick: () -> Unit,
    onNextBookClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val book by viewModel.getBookById(bookId).collectAsStateWithLifecycle(initialValue = null)
    val seriesBooksState: State<List<Book>>? = if (book?.seriesId != null) {
        viewModel.getBooksBySeries(book!!.seriesId).collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        null
    }
    val seriesBooks = seriesBooksState?.value ?: emptyList()
    val currentBookIndex = seriesBooks.indexOfFirst { it.id == book?.id }
    val nextBook = if (currentBookIndex >= 0) seriesBooks.getOrNull(currentBookIndex + 1) else null
    val bookmarkedPage = book?.bookmarkPage ?: -1

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var currentPfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isUnsupported by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val rendererMutex = remember { Mutex() }

    val pageCache = remember {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 4
        object : LruCache<Int, Bitmap>(cacheSize) {
            override fun sizeOf(key: Int, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    // Reader customization state
    var scrollMode by remember { mutableStateOf(ScrollMode.CONTINUOUS_VERTICAL) }
    var readerTheme by remember { mutableStateOf(ReaderThemeMode.NORMAL) }
    var showControls by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }

    // Initialize PdfRenderer safely with streaming fallback
    LaunchedEffect(book?.pdfUri, reloadTrigger) {
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { currentPfd?.close() } catch (_: Exception) {}
        pdfRenderer = null
        currentPfd = null
        pageCount = 0
        isUnsupported = false
        errorMessage = null
        pageCache.evictAll()

        val uriString = book?.pdfUri
        if (uriString.isNullOrBlank()) {
            return@LaunchedEffect
        }

        val uri = uriString.toUri()
        val result = openPdfRendererSafe(context, uri)
        if (result.renderer != null && result.pfd != null) {
            pdfRenderer = result.renderer
            currentPfd = result.pfd
            pageCount = result.renderer.pageCount
        } else {
            isUnsupported = true
            errorMessage = result.error
        }
    }

    // Pager state for horizontal mode
    val pagerState = rememberPagerState(
        initialPage = (book?.currentPage ?: 0).coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    // Sync initial restore page
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && book != null) {
            val initial = book!!.currentPage.coerceIn(0, pageCount - 1)
            listState.scrollToItem(initial)
            if (pagerState.currentPage != initial) {
                pagerState.scrollToPage(initial)
            }
        }
    }

    // Progress tracking for Continuous (Vertical)
    LaunchedEffect(listState, scrollMode, pageCount, book) {
        if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collectLatest { page ->
                    if (pageCount > 0 && book != null) {
                        viewModel.updateBookProgress(book!!, page)
                    }
                }
        }
    }

    // Progress tracking for Paged (Horizontal)
    LaunchedEffect(pagerState, scrollMode, pageCount, book) {
        if (scrollMode == ScrollMode.PAGED_HORIZONTAL) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collectLatest { page ->
                    if (pageCount > 0 && book != null) {
                        viewModel.updateBookProgress(book!!, page)
                    }
                }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { currentPfd?.close() } catch (_: Exception) {}
        }
    }

    val currentPageIndex by remember {
        derivedStateOf {
            if (scrollMode == ScrollMode.PAGED_HORIZONTAL) {
                pagerState.currentPage
            } else {
                listState.firstVisibleItemIndex
            }
        }
    }

    // Color Filters
    val colorFilter = remember(readerTheme) {
        when (readerTheme) {
            ReaderThemeMode.NORMAL -> null
            ReaderThemeMode.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0f, 0f, 0f, 20f,
                        0f, 0.90f, 0f, 0f, 10f,
                        0f, 0f, 0.80f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ReaderThemeMode.NIGHT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    val backgroundColor = when (readerTheme) {
        ReaderThemeMode.NORMAL -> Color(0xFF18181B)
        ReaderThemeMode.SEPIA -> Color(0xFF2B2520)
        ReaderThemeMode.NIGHT -> Color.Black
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (pdfRenderer == null) {
            Scaffold(
                containerColor = backgroundColor,
                topBar = {
                    TopAppBar(
                        title = {
                            val displayName = remember(book?.pdfUri) {
                                val raw = book?.pdfUri?.toUri()?.lastPathSegment ?: "Lector PDF"
                                val decoded = try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
                                decoded.substringAfterLast('/').substringAfterLast(':').ifBlank { "Lector PDF" }
                            }
                            Text(
                                text = displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUnsupported) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "No se pudo abrir el documento",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "El archivo no se pudo interpretar como PDF o no se tienen permisos de lectura.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { reloadTrigger++ },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reintentar")
                                }
                                Button(
                                    onClick = onBackClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Volver")
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Cargando documento...", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                }
            }
        } else {
            // Main reader canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls = !showControls
                    }
            ) {
                if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = if (showControls) 64.dp else 0.dp,
                            bottom = if (showControls) 96.dp else 0.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(pageCount) { index ->
                            PdfPageView(
                                renderer = pdfRenderer!!,
                                pageIndex = index,
                                mutex = rendererMutex,
                                colorFilter = colorFilter,
                                isBookmarked = index == bookmarkedPage,
                                pageCache = pageCache,
                                onToggleControls = { showControls = !showControls }
                            )
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = if (showControls) 64.dp else 0.dp,
                            bottom = if (showControls) 96.dp else 0.dp
                        )
                    ) { index ->
                        PdfPageView(
                            renderer = pdfRenderer!!,
                            pageIndex = index,
                            mutex = rendererMutex,
                            colorFilter = colorFilter,
                            isBookmarked = index == bookmarkedPage,
                            pageCache = pageCache,
                            onToggleControls = { showControls = !showControls }
                        )
                    }
                }
            }
        }

        // Top HUD Bar
        AnimatedVisibility(
            visible = showControls && pdfRenderer != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book?.pdfUri?.toUri()?.lastPathSegment ?: "Lector PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "Página ${currentPageIndex + 1} de $pageCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Bookmark toggle
                    IconButton(
                        onClick = {
                            book?.let { b ->
                                if (b.bookmarkPage == currentPageIndex) {
                                    viewModel.clearReadingBookmark(b)
                                } else {
                                    viewModel.setReadingBookmark(b, currentPageIndex)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (bookmarkedPage == currentPageIndex) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Marcador",
                            tint = if (bookmarkedPage == currentPageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Jump to page grid
                    IconButton(onClick = { showThumbnailsSheet = true }) {
                        Icon(Icons.Outlined.GridView, contentDescription = "Miniaturas")
                    }

                    // Reader Settings
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Ajustes de lectura")
                    }
                }
            }
        }

        // Bottom HUD Bar
        AnimatedVisibility(
            visible = showControls && pdfRenderer != null && pageCount > 0,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (nextBook != null && currentPageIndex >= pageCount - 1) {
                        Button(
                            onClick = { onNextBookClick(nextBook.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir siguiente volumen")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (currentPageIndex - 1).coerceAtLeast(0)
                                coroutineScope.launch {
                                    if (scrollMode == ScrollMode.PAGED_HORIZONTAL) {
                                        pagerState.animateScrollToPage(target)
                                    } else {
                                        listState.animateScrollToItem(target)
                                    }
                                }
                            },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Página anterior")
                        }

                        Slider(
                            value = (currentPageIndex + 1).toFloat(),
                            onValueChange = { newValue ->
                                val target = (newValue.roundToInt() - 1).coerceIn(0, pageCount - 1)
                                coroutineScope.launch {
                                    if (scrollMode == ScrollMode.PAGED_HORIZONTAL) {
                                        pagerState.scrollToPage(target)
                                    } else {
                                        listState.scrollToItem(target)
                                    }
                                }
                            },
                            valueRange = 1f..pageCount.toFloat(),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                val target = (currentPageIndex + 1).coerceAtMost(pageCount - 1)
                                coroutineScope.launch {
                                    if (scrollMode == ScrollMode.PAGED_HORIZONTAL) {
                                        pagerState.animateScrollToPage(target)
                                    } else {
                                        listState.animateScrollToItem(target)
                                    }
                                }
                            },
                            enabled = currentPageIndex < pageCount - 1
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Página siguiente")
                        }
                    }
                }
            }
        }

        // Reader Settings BottomSheet
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Ajustes de Lectura",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Mode: Paged vs Continuous
                    Text("Modo de Lectura", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            onClick = { scrollMode = ScrollMode.CONTINUOUS_VERTICAL },
                            selected = scrollMode == ScrollMode.CONTINUOUS_VERTICAL,
                            icon = { Icon(Icons.Default.SwapVert, null) }
                        ) {
                            Text("Continuo")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            onClick = { scrollMode = ScrollMode.PAGED_HORIZONTAL },
                            selected = scrollMode == ScrollMode.PAGED_HORIZONTAL,
                            icon = { Icon(Icons.Default.SwapHoriz, null) }
                        ) {
                            Text("Paginado")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Reading Theme
                    Text("Tono de Lectura", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            onClick = { readerTheme = ReaderThemeMode.NORMAL },
                            selected = readerTheme == ReaderThemeMode.NORMAL
                        ) {
                            Text("Normal")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            onClick = { readerTheme = ReaderThemeMode.SEPIA },
                            selected = readerTheme == ReaderThemeMode.SEPIA
                        ) {
                            Text("Sepia")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            onClick = { readerTheme = ReaderThemeMode.NIGHT },
                            selected = readerTheme == ReaderThemeMode.NIGHT
                        ) {
                            Text("Noche")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bookmark Shortcut
                    if (bookmarkedPage >= 0 && bookmarkedPage < pageCount) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (scrollMode == ScrollMode.PAGED_HORIZONTAL) {
                                        pagerState.scrollToPage(bookmarkedPage)
                                    } else {
                                        listState.scrollToItem(bookmarkedPage)
                                    }
                                    showSettingsSheet = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saltar al marcador (Página ${bookmarkedPage + 1})")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Thumbnails / Page Jump BottomSheet
        if (showThumbnailsSheet && pageCount > 0) {
            ModalBottomSheet(
                onDismissRequest = { showThumbnailsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Saltar a Página (Total: $pageCount)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(64.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items((0 until pageCount).toList()) { pIndex ->
                            val isCurrent = pIndex == currentPageIndex
                            val isMarked = pIndex == bookmarkedPage

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = if (isCurrent) 4.dp else 1.dp,
                                modifier = Modifier
                                    .aspectRatio(0.85f)
                                    .clickable {
                                        coroutineScope.launch {
                                            if (scrollMode == ScrollMode.PAGED_HORIZONTAL) {
                                                pagerState.scrollToPage(pIndex)
                                            } else {
                                                listState.scrollToItem(pIndex)
                                            }
                                            showThumbnailsSheet = false
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "${pIndex + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isMarked) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Componente de página individual para renderizar hojas PDF con PdfRenderer.
 * Consumido por LazyColumn y HorizontalPager en `PdfViewerScreen`.
 * Gestiona gestos de tap, zoom y pan con tolerancia para no bloquear el desplazamiento/scroleo
 * de los contenedores padre cuando la página no está ampliada (scale <= 1.05f).
 */
@Composable
fun PdfPageView(
    renderer: PdfRenderer,
    pageIndex: Int,
    mutex: Mutex,
    colorFilter: ColorFilter?,
    isBookmarked: Boolean,
    pageCache: LruCache<Int, Bitmap>,
    onToggleControls: () -> Unit = {}
) {
    val context = LocalContext.current
    val targetWidth = remember {
        val dm = context.resources.displayMetrics
        (dm.widthPixels * 1.5f).toInt().coerceIn(1080, 2560)
    }

    val bitmap by produceState<Bitmap?>(initialValue = pageCache.get(pageIndex), renderer, pageIndex) {
        if (value == null) {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        val cached = pageCache.get(pageIndex)
                        if (cached != null) {
                            value = cached
                        } else {
                            val page = renderer.openPage(pageIndex)
                            val height = (targetWidth * page.height / page.width)
                            val bmp = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            pageCache.put(pageIndex, bmp)
                            value = bmp
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        value = null
                    }
                }
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.2f
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectZoomPanGestures(
                    onZoomPan = { zoomChange, panChange ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                        scale = newScale
                        if (newScale > 1.05f) {
                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                            val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                            offset = Offset(
                                x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    },
                    isZoomed = { scale > 1.05f }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Página ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 2.dp)
            }
        }

        if (isBookmarked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Marcador",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private data class PdfOpenResult(
    val renderer: PdfRenderer?,
    val pfd: ParcelFileDescriptor?,
    val error: String? = null
)

private suspend fun openPdfRendererSafe(context: Context, uri: Uri): PdfOpenResult = withContext(Dispatchers.IO) {
    // 1. Try file scheme directly
    if (uri.scheme == "file") {
        try {
            val f = File(uri.path ?: "")
            if (f.exists() && f.canRead()) {
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(pfd)
                    return@withContext PdfOpenResult(renderer, pfd)
                } catch (e: Exception) {
                    try { pfd.close() } catch (_: Exception) {}
                    return@withContext PdfOpenResult(null, null, "El archivo PDF no se pudo procesar: ${e.localizedMessage ?: "formato no válido"}")
                }
            }
        } catch (e: Exception) {
            Log.w("PdfViewerScreen", "File scheme direct open failed", e)
        }
    }

    // 2. Try content scheme via openFileDescriptor
    if (uri.scheme == "content") {
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                try {
                    val renderer = PdfRenderer(pfd)
                    return@withContext PdfOpenResult(renderer, pfd)
                } catch (e: Exception) {
                    Log.w("PdfViewerScreen", "Direct PdfRenderer(pfd) failed, trying cache copy", e)
                    try { pfd.close() } catch (_: Exception) {}
                    pfd = null
                }
            }
        } catch (e: Exception) {
            Log.w("PdfViewerScreen", "Direct openFileDescriptor failed, trying cache copy", e)
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    // 3. Robust Fallback: Copy stream into a seekable file in cacheDir
    // Solves non-seekable file descriptors (pipes), permission scope issues, virtual documents, etc.
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            val cacheFile = File(context.cacheDir, "temp_reader_${abs(uri.toString().hashCode())}.pdf")
            inputStream.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(pfd)
                    return@withContext PdfOpenResult(renderer, pfd)
                } catch (e: Exception) {
                    try { pfd.close() } catch (_: Exception) {}
                    return@withContext PdfOpenResult(null, null, "El documento no es un PDF válido o está dañado")
                }
            }
        }
    } catch (e: Exception) {
        Log.e("PdfViewerScreen", "InputStream cache copy failed", e)
        return@withContext PdfOpenResult(null, null, "No se pudo acceder al archivo: ${e.localizedMessage ?: "error de lectura"}")
    }

    PdfOpenResult(null, null, "No se pudo abrir el archivo PDF")
}

private suspend fun PointerInputScope.detectZoomPanGestures(
    onZoomPan: (zoomChange: Float, panChange: Offset) -> Unit,
    isZoomed: () -> Boolean
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val pointerCount = event.changes.size
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!isZoomed() && pointerCount < 2) {
                    continue
                }

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1f - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop || (isZoomed() && panMotion > touchSlop) || pointerCount >= 2) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onZoomPan(zoomChange, panChange)
                    }
                    event.changes.forEach {
                        if (it.positionChange() != Offset.Zero) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

