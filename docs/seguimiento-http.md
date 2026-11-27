# DF-11 · Seguimiento

GET /api/perfiles/{idPerfilUsuario}/seguimiento?fecha=YYYY-MM-DD usa hoy si se omite la fecha. GET /api/perfiles/{idPerfilUsuario}/seguimiento/{idActividad}?fecha=YYYY-MM-DD devuelve el detalle de una fila para esa fecha. Ambas rutas son de solo lectura, requieren cuidador propietario de un perfil activo y devuelven Cache-Control no-store. Una fecha inválida o fuera de rango produce 400. Los recursos ajenos o ausentes devuelven 404.

La respuesta de lista devuelve fecha, fechaMinima (creación del perfil), fechaActual, zonaHoraria y actualizarEnSegundos, tieneActividadesActivas, tieneHistorial, cobertura, resumen y actividades. Las filas incluyen idActividad, revision, nombre, hora HH:mm, codigoIcono, estado, pasosCompletados y totalPasos. El detalle devuelve fecha, actividad y pasos en orden con pictograma y estado. Una finalización directa cuenta como completada y los pasos sin evidencia se muestran como Sin registro.

La cobertura COMPLETA incluye todas las actividades programadas al cierre más cualquier ejecución del día. Las ejecuciones prevalecen con la versión fijada. Para hoy se consulta la configuración activa actual más las ejecuciones. El resumen devuelve total, completadas, enProgreso, pendientes y porcentaje redondeado o nulo si el total es cero. La cobertura PARCIAL devuelve únicamente ejecuciones conocidas y resumen nulo. No se reconstruye el denominador de fechas anteriores.

## Transición

1. Probar la transición en una copia MySQL desechable con DF-09 aplicado. Confirmar la zona de los timestamps de creación de perfiles. Por defecto se interpretan en la zona de agenda (America/Lima). Si la base los almacenó con otra hora civil, configurar diafacil.seguimiento.zona-fecha-creacion con esa zona antes de arrancar.
2. Detener todos los escritores y tomar respaldo. En la sesión MySQL fijar @df11_fecha_inicio al día actual del reloj de agenda, con formato YYYY-MM-DD. Ejecutar add-seguimiento.sql en esa sesión y comprobar una fila de cobertura, una entrada para cada actividad activa y el orden/pictogramas de los pasos. Evitar la medianoche durante el corte.
3. Arrancar el backend actualizado después de confirmar la transición. Las tablas se instalan con el script, también en ambientes nuevos después de crear el esquema operativo. Si se interrumpe el DDL, revisar la etapa y restaurar el respaldo o completar la transición con el backend detenido. No volver a ejecutar el script entero sobre un esquema que ya contiene las tablas.

El registro se actualiza junto a creación, edición y eliminación bajo el bloqueo del perfil. Guarda únicamente los cambios por día y es independiente de las claves externas de actividad y pasos borrables. La política de borrado de DF-10 sigue aplicándose a las entidades operativas.
