---
name: modificar-sin-romper-conexiones
description: Usar este skill cada vez que se vaya a agregar, modificar o eliminar una funcion, componente, ventana o endpoint que pueda estar conectado a otras partes del sistema.
---

# Skill: Modificar sin romper conexiones

## Cuando usar este skill
Se activa automaticamente antes de:
- Eliminar o renombrar una funcion, metodo o componente.
- Cambiar la firma de una funcion (parametros, tipo de retorno).
- Desconectar un boton, evento o handler de una ventana.
- Eliminar un endpoint o cambiar su ruta.

## Pasos obligatorios

Paso 1: Buscar la referencia.
Antes de tocar el elemento, buscarlo en docs/CONNECTIONS.md y en docs/ARCHITECTURE.md para ver si aparece como "Usado por" o dentro de "Puntos criticos de ruptura".

Paso 2: Evaluar impacto.
Si el elemento aparece conectado a otro modulo, listar explicitamente que otros archivos o funciones se veran afectados antes de hacer el cambio.

Paso 3: Confirmar con el usuario si hay riesgo.
Si el cambio afecta a mas de un modulo, o si es una conexion marcada como critica, detenerse y preguntar antes de ejecutar el cambio. No asumir que esta bien.

Paso 4: Ejecutar el cambio.
Aplicar la modificacion en el codigo.

Paso 5: Actualizar la documentacion en el mismo paso.
- Si la conexion cambio: actualizar la entrada correspondiente en docs/CONNECTIONS.md (estado, fecha, nota).
- Si un modulo cambio de proposito o de dependencias: actualizar docs/ARCHITECTURE.md.
- Nunca dejar esta actualizacion para el final de la sesion.

Paso 6: Registrar en la bitacora.
Agregar una linea breve en docs/BITACORA.md dentro de la sesion actual, indicando que conexion se toco y por que.

## Regla de oro
Si no se puede confirmar con certeza que un elemento no esta conectado a nada mas, tratarlo como si estuviera conectado. Preguntar es mas barato que romper.
