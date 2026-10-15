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

Conexion: [ejemplo]
Origen:
Destino:
Tipo:
Estado: activa
Fecha ultima modificacion:
Nota:

---

## Conexiones eliminadas (historial corto)
Mantener solo las ultimas 10, para no inflar el archivo. El historial largo va en BITACORA.md.

Conexion: [ejemplo]
Motivo de eliminacion:
Fecha:
