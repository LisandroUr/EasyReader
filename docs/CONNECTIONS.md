# CONNECTIONS.md

Registro vivo de conexiones entre partes del sistema. Se actualiza EN EL MOMENTO en que se crea, modifica o elimina una conexion, no al final de la sesion.

## Formato por entrada
Copiar este bloque por cada conexion relevante:

Conexion: [nombre descriptivo]
Origen: [funcion / componente / archivo que dispara o contiene la conexion]
Destino: [funcion / componente / archivo que la recibe o consume]
Tipo: [ej: evento de UI, llamada a API, lectura de base de datos, importacion de modulo]
Estado: activa
Fecha ultima modificacion: [fecha]
Nota: [cualquier detalle que evite que se rompa por error]

---

## Conexiones activas

Conexion: Navegación Principal NavHost
Origen: `MainActivity.kt` (`EasyReaderApp`)
Destino: `HomeScreen`, `SeriesDetailScreen`, `PdfViewerScreen`, `SettingsScreen`
Tipo: Evento de UI / Navegación Jetpack Compose Navigation
Estado: activa
Fecha ultima modificacion: 2026-10-15
Nota: Administra el flujo entre pantallas. Las rutas incluyen parámetros tipados (`seriesId`, `bookId`).

Conexion: Carga y Observación Reactiva de Biblioteca
Origen: `HomeScreen.kt`, `SeriesDetailScreen.kt`, `SettingsScreen.kt`
Destino: `LibraryViewModel.kt` (`allSeries`, `allBooks`, `recentlyReadBooks`, `seriesBookCounts`, `searchQuery`)
Tipo: Suscripción a datos UI (`StateFlow` / `collectAsStateWithLifecycle`)
Estado: activa
Fecha ultima modificacion: 2026-10-15
Nota: Las pantallas se reactualizan automáticamente ante cualquier cambio en Room. Aplicado rediseño con la paleta de colección "Estudio Editorial Nocturno".

Conexion: Operaciones CRUD de Series y Libros
Origen: `LibraryViewModel.kt`
Destino: `LibraryRepository.kt`
Tipo: Llamada a método de capa de repositorio
Estado: activa
Fecha ultima modificacion: 2026-09-12
Nota: Toda modificación de datos pasa por `LibraryViewModel` en corrutinas `viewModelScope`.

Conexion: Consultas a la Base de Datos Room
Origen: `LibraryRepository.kt`
Destino: `EasyReaderDatabase.kt` / `SeriesDao.kt` / `BookDao.kt`
Tipo: Lectura/Escritura de base de datos SQLite
Estado: activa
Fecha ultima modificacion: 2026-09-12
Nota: Incluye `Flow` reactivos. La eliminación de `Series` borra sus `Book` asociados por clave foránea en cascada (`ForeignKey.CASCADE`).

Conexion: Monitoreo de Carpetas SAF en Tiempo Real
Origen: `LibraryRepository.startWatchingFolder`
Destino: Android OS `ContentResolver.registerContentObserver` -> `LibraryRepository.importRecursively`
Tipo: Callback de evento de sistema / ContentObserver
Estado: activa
Fecha ultima modificacion: 2026-09-12
Nota: Utiliza un mecanismo de debounce de 2000ms e interbloqueos (`globalMutex`, `folderMutexes`) para evitar concurrencia y reentrancia durante la importación.

Conexion: Sincronización Periódica en Segundo Plano
Origen: `LibraryViewModel.scheduleLibrarySyncWork`
Destino: Android `WorkManager` -> `LibrarySyncWorker.kt` -> `LibraryRepository.rescanAllBoundFolders`
Tipo: Trabajo en segundo plano (PeriodicWorkRequest)
Estado: activa
Fecha ultima modificacion: 2026-09-12
Nota: Nombre único de trabajo `"library_sync_work"`. Sincroniza carpetas vinculadas según la frecuencia configurada por el usuario (1h, 6h, 24h) o se desactiva si es 0 (Manual).

Conexion: Renderizado de PDF y Carga de Páginas
Origen: `PdfViewerScreen.kt` (`openPdfRendererSafe`, `PdfPageView`, `detectZoomPanGestures`)
Destino: Android Native `PdfRenderer` / `ParcelFileDescriptor` / Compose `LazyColumn` & `HorizontalPager`
Tipo: Llamada a API de renderizado nativo / Gestor de gestos y desplazamiento
Estado: activa
Fecha ultima modificacion: 2026-10-15
Nota: Apertura segura con fallback de copia a `cacheDir`. `PdfRenderer` y `ParcelFileDescriptor` deben cerrarse rigurosamente al cambiar de documento o destruir la vista. Implementado detector de gestos personalizado `detectZoomPanGestures` para permitir el scroleo vertical u horizontal fluido en los contenedores padre cuando scale <= 1.05f.

Conexion: Generación de Miniaturas/Portadas de PDF
Origen: `LibraryRepository.createBookFromUri`
Destino: `PdfHelper.generateThumbnail` -> `context.filesDir`
Tipo: Procesamiento I/O y renderizado de imagen
Estado: activa
Fecha ultima modificacion: 2026-09-12
Nota: Extrae la primera página del PDF y la guarda como PNG en el almacenamiento privado de la app.

Conexion: Validación y Detección de Formato PDF
Origen: `LibraryRepository.importRecursively`, `createBookFromUri`, `isPdfBookUri`
Destino: `BookFormatUtils.kt` (`isPdf`, `isPdfUri`, `isSupported`)
Tipo: Función utilitaria
Estado: activa
Fecha ultima modificacion: 2026-09-12
Nota: Combina inspección de MIME Type, extensión del archivo, URL decoding y verificación de firma de cabecera mágica `%PDF-`.

Conexion: Exportación e Importación de Paquetes .easyreader
Origen: `LibraryRepository.exportSeries` / `importSeries`
Destino: `ZipHelper.kt`, `Moshi` (`SeriesExportMetadata`, `BookExportMetadata`), `FileProvider`
Tipo: Empaquetado I/O / Serialización JSON
Estado: activa
Fecha ultima modificacion: 2026-09-12
Nota: Empaqueta los PDFs, portadas y `metadata.json` en un zip `.easyreader`.

Conexion: Copia de Seguridad y Restauración de Biblioteca
Origen: `LibraryRepository.backupLibrary` / `restoreLibrary`
Destino: `EasyReaderDatabase.closeDatabase()`, `ZipHelper.kt`, `filesDir`, SQLite DB files
Tipo: Operaciones I/O de archivos de sistema / Reemplazo de BD
Estado: activa
Fecha ultima modificacion: 2026-09-12
Nota: Requiere cerrar primero la base de datos de Room en ejecución antes de sobrescribir los archivos de la base de datos SQLite.

Conexion: Compartición mediante Intents del Sistema
Origen: `SeriesDetailScreen.kt`, `SettingsScreen.kt`
Destino: Android OS `Intent.ACTION_SEND` (`createChooser`)
Tipo: Intent de sistema Android
Estado: activa
Fecha ultima modificacion: 2026-10-15
Nota: Utiliza `FileProvider` (`com.example.easyreader.fileprovider`) para compartir los archivos generados en `cacheDir/shared/`.

---

## Conexiones eliminadas (historial corto)
(No hay conexiones eliminadas).
