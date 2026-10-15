# ARCHITECTURE.md

Este archivo es el MAPA ACTUAL del sistema. No es historial, es el estado presente.
Se actualiza cada vez que algo cambia de lugar o de funcion. Si esta desactualizado, es peor que no tenerlo.

## Modulos principales

### MainActivity & Navegación (NavHost)
- Ubicacion: `app/src/main/java/com/example/easyreader/MainActivity.kt`
- Proposito: Punto de entrada de la app en Android. Inicializa la pantalla de inicio (SplashScreen), habilita bordes edge-to-edge, aplica el tema `EasyReaderTheme` e instala el mapa de rutas con Navigation Compose (`home`, `series_detail/{seriesId}`, `viewer/{bookId}`, `settings`).
- Depende de: `LibraryViewModel`, `HomeScreen`, `SeriesDetailScreen`, `PdfViewerScreen`, `SettingsScreen`, `EasyReaderTheme`.
- Usado por: Sistema operativo Android (declarado como Activity ejecutable en `AndroidManifest.xml`).
- Advertencia: No cambiar los nombres ni tipos de argumentos de las rutas en `NavHost` (`seriesId`, `bookId`) sin actualizar las llamadas `navController.navigate(...)` en `HomeScreen`, `SeriesDetailScreen` y `PdfViewerScreen`.

### ViewModel Principal (`LibraryViewModel`)
- Ubicacion: `app/src/main/java/com/example/easyreader/ui/LibraryViewModel.kt`
- Proposito: Capa de estado y lógica de presentación. Expone los Flows reactivos del repositorio como `StateFlow` para Compose, gestiona las preferencias del usuario (`SharedPreferences`), programa el trabajo periódico con `WorkManager` y ejecuta tareas asíncronas en `viewModelScope`.
- Depende de: `LibraryRepository`, `LibrarySyncWorker`, `WorkManager`, `SharedPreferences`.
- Usado por: `MainActivity`, `HomeScreen`, `SeriesDetailScreen`, `PdfViewerScreen`, `SettingsScreen`.
- Advertencia: No modificar los métodos expuestos ni el ciclo de inicialización sin revisar cómo responden las vistas Compose que observan sus `StateFlow`.

### Pantalla Principal (`HomeScreen`)
- Ubicacion: `app/src/main/java/com/example/easyreader/ui/HomeScreen.kt`
- Proposito: Pantalla inicial con pestañas para Series, Todos los Libros y Lecturas Recientes. Proporciona barra de búsqueda global, ordenamiento, toggle rejilla/lista, botón de sincronización manual, navegación a ajustes y FAB para crear/importar series y vincular carpetas maestras.
- Depende de: `LibraryViewModel`, `SeriesGridCard`, `SeriesListItem`, `BookGridCard`, `BookListItem`, `RecentBookBigCard`, `SearchResultsView`, contratos SAF (`OpenDocumentTree`, `GetContent`).
- Usado por: `MainActivity` (Ruta `"home"`).
- Advertencia: Los callbacks `onSeriesClick`, `onBookClick` y `onSettingsClick` se comunican con `NavHost`. Desconectarlos impide la navegación a los detalles o al lector.

### Pantalla Detalle de Serie (`SeriesDetailScreen`)
- Ubicacion: `app/src/main/java/com/example/easyreader/ui/SeriesDetailScreen.kt`
- Proposito: Vista detallada de una serie específica. Muestra la cabecera con portada difuminada, estadísticas, lista ordenada de libros, búsqueda interna, modo de reordenamiento de volúmenes, importación de PDFs, vinculación SAF de carpeta, limpieza de duplicados y exportación de serie (.easyreader).
- Depende de: `LibraryViewModel`, `SeriesBookRow`, contratos SAF (`OpenMultipleDocuments`, `OpenDocumentTree`, `GetContent`), Android `Intent`.
- Usado por: `MainActivity` (Ruta `"series_detail/{seriesId}"`).
- Advertencia: Al reordenar libros, se intercambia la propiedad `customOrder` en la base de datos. Si se rompe el orden de actualización, la ordenación visual fallará.

### Visor de PDF (`PdfViewerScreen`)
- Ubicacion: `app/src/main/java/com/example/easyreader/ui/PdfViewerScreen.kt`
- Proposito: Lector de PDFs integrado usando `PdfRenderer` nativo de Android. Soporta desplazamiento vertical continuo o modo paginado horizontal, gestos de zoom, control de progreso automático, marcadores, cuadrícula de miniaturas para salto rápido, temas de lectura (Normal, Sepia, Noche) y botón de rápido acceso al siguiente volumen.
- Depende de: `LibraryViewModel`, `PdfPageView`, `PdfRenderer`, `ParcelFileDescriptor`, `LruCache`, `openPdfRendererSafe`.
- Usado por: `MainActivity` (Ruta `"viewer/{bookId}"`).
- Advertencia: `PdfRenderer` y `ParcelFileDescriptor` deben cerrarse rigurosamente en `DisposableEffect` y en la corrutina de inicialización para no saturar descriptores de archivo ni memoria nativa.

### Pantalla de Ajustes (`SettingsScreen`)
- Ubicacion: `app/src/main/java/com/example/easyreader/ui/SettingsScreen.kt`
- Proposito: Configuración general de la aplicación. Permite modificar el tema (Auto, Claro, Oscuro, OLED), Color Dinámico Material You, frecuencia de sincronización en segundo plano, copia de seguridad y restauración completa de la biblioteca (.zip) e importación de paquetes `.easyreader`.
- Depende de: `LibraryViewModel`, contratos SAF (`OpenDocument`), Android `Intent`.
- Usado por: `MainActivity` (Ruta `"settings"`).
- Advertencia: La restauración de la base de datos sobrescribe directamente `easy_reader_database` y requiere cerrar previamente la instancia activa de Room en `EasyReaderDatabase.closeDatabase()`.

### Repositorio de Datos (`LibraryRepository`)
- Ubicacion: `app/src/main/java/com/example/easyreader/data/LibraryRepository.kt`
- Proposito: Fuente única de verdad. Interactúa con Room (`SeriesDao`, `BookDao`), supervisa cambios en carpetas del sistema mediante `ContentObserver`, ejecuta escaneos recursivos (`importRecursively`), genera miniaturas de PDFs (`PdfHelper`), exporta e importa paquetes zip con metadata JSON (`ZipHelper`, `Moshi`) y gestiona respaldos completos de la BD.
- Depende de: `EasyReaderDatabase`, `SeriesDao`, `BookDao`, `BookFormatUtils`, `PdfHelper`, `ZipHelper`, `Moshi`, `ContentResolver`, SAF `DocumentFile`.
- Usado por: `LibraryViewModel`, `LibrarySyncWorker`.
- Advertencia: Mantiene `folderObservers` y mutexes de concurrencia (`globalMutex`, `folderMutexes`). Modificar la lógica de cerrojos puede ocasionar bucles infinitos de importación o reentrancia con `ContentObserver`.

### Base de Datos y Modelos Room (`EasyReaderDatabase`, `Book`, `Series`, `BookDao`, `SeriesDao`, `SharingModels`)
- Ubicacion: `app/src/main/java/com/example/easyreader/data/`
- Proposito:
  - `Book`: Entidad Room para libros PDF. Incluye FK en cascada a `Series` y un índice único compuesto `(seriesId, pdfUri)`.
  - `Series`: Entidad Room para colecciones/carpetas vinculadas.
  - `EasyReaderDatabase`: Base de datos SQLite v6 con migraciones MIGRATION_3_4, MIGRATION_4_5 y MIGRATION_5_6.
  - `BookDao` / `SeriesDao`: Interfaces DAO con consultas SQL y retorno de `Flow`.
  - `SharingModels`: Estructuras Moshi JSON para metadatos de exportación `.easyreader`.
- Depende de: Room Database Engine, Moshi.
- Usado por: `LibraryRepository`.
- Advertencia: La clave foránea en `Book` elimina libros en cascada cuando se borra la `Series`. El índice único en `(seriesId, pdfUri)` requiere verificar o ignorar duplicados antes de insertar.

### Utilidades (`BookFormatUtils`, `PdfHelper`, `ZipHelper`)
- Ubicacion: `app/src/main/java/com/example/easyreader/util/`
- Proposito:
  - `BookFormatUtils`: Detección de archivos PDF por MIME Type (`application/pdf`), extensión (`.pdf`), URL decoding y firma de cabecera mágica `%PDF-`.
  - `PdfHelper`: Renderiza la primera página de un PDF como PNG para usar de portada/miniatura en `filesDir`.
  - `ZipHelper`: Empaquetado y descompresión de archivos ZIP con protección explícita contra ataques Zip Slip.
- Depende de: Android SDK, Java I/O / Zip.
- Usado por: `LibraryRepository`, pruebas unitarias (`BookFormatUtilsTest`, `ZipHelperTest`).
- Advertencia: `ZipHelper.unzip` lanza `SecurityException` si una entrada intenta salir del directorio destino (`Zip Slip`). No eliminar esta validación.

### Trabajador en Segundo Plano (`LibrarySyncWorker`)
- Ubicacion: `app/src/main/java/com/example/easyreader/worker/LibrarySyncWorker.kt`
- Proposito: Tarea periódica de WorkManager que invoca `LibraryRepository.rescanAllBoundFolders()` para actualizar en segundo plano las carpetas vinculadas.
- Depende de: `LibraryRepository`, `WorkManager`.
- Usado por: `WorkManager` (programado desde `LibraryViewModel.scheduleLibrarySyncWork`).
- Advertencia: Se identifica con el nombre único `"library_sync_work"`.

## Flujo general del sistema

1. **Inicio de la Aplicación**:
   `MainActivity` inicializa `LibraryViewModel`. `LibraryViewModel` programa el `LibrarySyncWorker` con el intervalo configurado y arranca los `ContentObserver` para las series que poseen un `folderUri`.

2. **Navegación y Consulta de Datos**:
   Las pantallas (`HomeScreen`, `SeriesDetailScreen`, `SettingsScreen`) se suscriben a los `StateFlow` expuestos por `LibraryViewModel`, los cuales obtienen datos en tiempo real desde Room mediante `Flow` reactivos en `LibraryRepository`.

3. **Sincronización e Importación de Contenidos**:
   Cuando el usuario vincula una carpeta (o cuando el SO notifica un cambio vía `ContentObserver` o `LibrarySyncWorker` se ejecuta), `LibraryRepository.importRecursively` recorre el árbol de archivos. Para cada PDF detectado con `BookFormatUtils`, llama a `PdfHelper.generateThumbnail` y persiste el `Book` en `BookDao`.

4. **Lectura de Documentos**:
   Al pulsar un libro, `MainActivity` navega a `PdfViewerScreen`. Se abre el archivo mediante `openPdfRendererSafe` (usando FD directo o copia en `cacheDir` como fallback). El usuario lee el documento y `LibraryViewModel.updateBookProgress` guarda la página actual y timestamp en tiempo real.

5. **Compartición y Copias de Seguridad**:
   Desde `SeriesDetailScreen` o `SettingsScreen`, `LibraryRepository` comprime los archivos en `.easyreader` o `.zip` y genera un URI seguro con `FileProvider` para compartir mediante `Intent`. En la restauración, desacopla la BD con `EasyReaderDatabase.closeDatabase()` antes de reemplazar los archivos físicos.

## Puntos criticos de ruptura

- **Reemplazo Físico de Base de Datos en Restauración**: Si `restoreLibrary` intenta escribir `easy_reader_database` sin haber ejecutado `EasyReaderDatabase.closeDatabase()`, Room o SQLite bloquearán el archivo provocando corrupción o un crash.
- **Permisos Persistentes SAF (`takePersistableUriPermission`)**: Al seleccionar carpetas o archivos mediante SAF, se debe invocar `takePersistableUriPermission`. Si el SO revoca el permiso o no se solicita correctamente, `ContentObserver` y `DocumentFile` fallarán silenciosamente o con `SecurityException`.
- **Desbloqueo de Descriptores en `PdfViewerScreen`**: Si la pantalla del visor se destruye sin cerrar `PdfRenderer` y `ParcelFileDescriptor`, el sistema agotará los File Descriptors provocando que ningún otro PDF pueda abrirse hasta reiniciar la app.
- **Validación Zip Slip en `ZipHelper`**: Eliminar la comprobación `canonicalPath.startsWith` expone la app a sobrescritura arbitraria de archivos en almacenamiento interno durante la importación de respaldos.
