# AGENTS.md

## Proyecto
EasyReader - Aplicación Android para gestión de biblioteca de libros/cómics PDF, sincronización automática de carpetas locales y lector integrado.

## Stack técnico
- Lenguaje / framework principal: Kotlin 2.3.21 / Jetpack Compose / Material 3
- Gestor de dependencias: Gradle Version Catalog (`gradle/libs.versions.toml`)
- Base de datos (si aplica): Room Database v6 (`easy_reader_database` - SQLite)
- Versión mínima requerida: Android 10 (API Level 29) - Target SDK 36 (Android 16)
- Componentes clave: AndroidX Navigation Compose, AndroidX WorkManager, Moshi, Coil Compose, Android `PdfRenderer`

## Reglas de trabajo para el agente
- Antes de modificar cualquier archivo, leer primero `docs/ARCHITECTURE.md` y `docs/CONNECTIONS.md`.
- Nunca eliminar, renombrar o desconectar una funcion, componente o endpoint sin antes verificar en `docs/CONNECTIONS.md` si esta siendo usado por otra parte del sistema.
- Si se detecta que un cambio rompe una conexion listada, detenerse y preguntar antes de continuar.
- Cada vez que se cree, modifique o elimine una conexion entre modulos (por ejemplo: una funcion que alimenta una ventana, un boton que dispara un evento, un servicio que consume una API), se debe actualizar `docs/CONNECTIONS.md` en el mismo cambio, no despues.
- Cada sesion de trabajo debe cerrar con una actualizacion de `docs/BITACORA.md` siguiendo la plantilla definida ahi.
- No usar dependencias nuevas sin dejarlo anotado en este archivo, seccion Stack tecnico.

## Convenciones de codigo
- Nombres de funciones y componentes: camelCase para funciones/métodos (`getSeriesById`, `importRecursively`), PascalCase para Composables (`HomeScreen`, `PdfViewerScreen`, `SeriesGridCard`).
- Comentarios obligatorios en toda funcion que sea punto de conexion entre modulos, indicando que la consume y por que no se debe desconectar sin revisar.
- Maximo de lineas por funcion: 50 (se exceptúan vistas complejas de Compose con layouts declarativos extensos).
- Maximo de lineas por archivo: 500

## Seguridad y limites
- No commitear credenciales, tokens ni archivos de entorno.
- No modificar archivos fuera del directorio del proyecto.
- Cambios estructurales grandes (renombrar carpetas, migrar librerias) requieren confirmacion explicita antes de ejecutarse.
