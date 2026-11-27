# DF-11 · Seguimiento

- `GET /api/perfiles/{idPerfilUsuario}/seguimiento?fecha=YYYY-MM-DD` consulta el resumen y las actividades del día.
- `GET /api/perfiles/{idPerfilUsuario}/seguimiento/{idActividad}?fecha=YYYY-MM-DD` devuelve el detalle de una fila para esa fecha.

En ambas rutas, omitir `fecha` consulta el día actual. Las dos rutas son de solo lectura, requieren cuidador propietario de un perfil activo y devuelven Cache-Control no-store. Una fecha inválida o fuera de rango produce 400. Los recursos ajenos o ausentes devuelven 404.

La respuesta de lista devuelve fecha, fechaMinima (creación del perfil), fechaActual, zonaHoraria y actualizarEnSegundos, tieneActividadesActivas, tieneHistorial, cobertura, resumen y actividades. Las filas incluyen idActividad, revision, nombre, hora HH:mm, codigoIcono, estado, pasosCompletados y totalPasos. El detalle devuelve fecha, actividad y pasos en orden con pictograma y estado. Una finalización directa cuenta como completada y los pasos sin evidencia se muestran como Sin registro.

La cobertura COMPLETA incluye todas las actividades programadas al cierre más cualquier ejecución del día. Las ejecuciones prevalecen con la versión fijada. Para hoy se consulta la configuración activa actual más las ejecuciones. El resumen devuelve total, completadas, enProgreso, pendientes y porcentaje redondeado o nulo si el total es cero. La cobertura PARCIAL devuelve únicamente ejecuciones conocidas y resumen nulo. No se reconstruye el denominador de fechas anteriores.

## Transición

1. Probar la transición en una copia MySQL desechable con DF-09 aplicado. Confirmar la zona de los timestamps de creación de perfiles. Por defecto se interpretan en la zona de agenda (America/Lima). Si la base los almacenó con otra hora civil, configurar diafacil.seguimiento.zona-fecha-creacion con esa zona antes de arrancar.
2. Detener todos los escritores y tomar respaldo. En la sesión MySQL fijar @df11_fecha_inicio al día actual del reloj de agenda, con formato YYYY-MM-DD. Ejecutar [`docs/sql/add-seguimiento.sql`](sql/add-seguimiento.sql) en esa sesión y comprobar una fila de cobertura, una entrada para cada actividad activa y el orden/pictogramas de los pasos. Evitar la medianoche durante el corte.
3. Arrancar el backend actualizado después de confirmar la transición. Las tablas se instalan con el script, también en ambientes nuevos después de crear el esquema operativo. Si se interrumpe el DDL, revisar la etapa y restaurar el respaldo o completar la transición con el backend detenido. No volver a ejecutar el script entero sobre un esquema que ya contiene las tablas.

El script crea `seguimiento_cobertura` y `seguimiento_rutina` y registra la configuración activa en la fecha de corte. Después, creación, edición y eliminación registran cambios por día dentro de la misma transacción y bajo el bloqueo del perfil. Una retirada conserva la vigencia para los días anteriores y excluye la actividad de la rutina al cierre de ese día. Si hubo una ejecución, esta prevalece con su versión fijada. El registro histórico no tiene claves externas hacia las actividades ni sus pasos, por lo que [la eliminación híbrida de DF-10](actividades-http.md#eliminación-df-10) puede borrar definiciones operativas sin perder la evidencia histórica registrada desde el corte.

La responsable del proyecto confirmó la aplicación del script en su base de desarrollo y la validación funcional en Android. Queda pendiente comprobar la concurrencia en una base MySQL aislada, en particular la consistencia de las consultas durante el inicio, avance y cierre de una ejecución y durante cambios concurrentes en la rutina.
