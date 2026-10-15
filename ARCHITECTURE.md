# ARCHITECTURE.md

Este archivo es el MAPA ACTUAL del sistema. No es historial, es el estado presente.
Se actualiza cada vez que algo cambia de lugar o de funcion. Si esta desactualizado, es peor que no tenerlo.

## Modulos principales

### [Nombre del modulo, ej: Ventana de Login]
- Ubicacion: [ruta del archivo]
- Proposito: [que hace]
- Depende de: [que otros modulos o funciones necesita para funcionar]
- Usado por: [que otros modulos lo llaman o lo consumen]
- Advertencia: [ej: no eliminar la funcion validarSesion sin revisar Dashboard.js]

### [Nombre del modulo 2]
- Ubicacion:
- Proposito:
- Depende de:
- Usado por:
- Advertencia:

## Flujo general del sistema
[Descripcion corta en texto de como se conectan los modulos principales entre si, en orden logico]

## Puntos criticos de ruptura
Lista de conexiones fragiles que un cambio descuidado podria romper.
- [ej: El boton Guardar en Ventana Perfil llama directamente a la funcion actualizarBD, que tambien usa el modulo de Reportes. Cualquier cambio en su firma rompe ambos.]
