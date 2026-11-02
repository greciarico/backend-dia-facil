-- Incremento 1: AYUDA Y NOTIFICACIONES INTERNAS.
-- PROPUESTA: NO APLICADA. Requiere aprobacion expresa del usuario.
-- Detener el backend antes de aplicar. MySQL DDL realiza commit implicito.
-- Hacer copia de seguridad del esquema/datos y comprobar DATABASE().
-- No ejecutar si alguna columna/clave propuesta ya existe: auditar primero.
SELECT DATABASE() AS esquema;
SHOW CREATE TABLE solicitud_ayuda;
SHOW CREATE TABLE notificacion_cuidador;
-- Estas dos consultas deben devolver CERO filas antes de agregar UNIQUE.
SELECT id_solicitud_ayuda, COUNT(*) AS cantidad
FROM notificacion_cuidador WHERE id_solicitud_ayuda IS NOT NULL
GROUP BY id_solicitud_ayuda HAVING COUNT(*) > 1;
SELECT id_ejecucion_actividad, COUNT(*) AS cantidad
FROM notificacion_cuidador WHERE id_ejecucion_actividad IS NOT NULL
GROUP BY id_ejecucion_actividad HAVING COUNT(*) > 1;

-- Las filas historicas mantienen NULL; no se inventan claves ni contexto pasado.
ALTER TABLE solicitud_ayuda
  ADD COLUMN clave_operacion VARCHAR(36) NULL,
  ADD COLUMN posicion_paso INT NULL,
  ADD COLUMN descripcion_paso VARCHAR(120) NULL,
  ADD UNIQUE KEY uq_ayuda_perfil_operacion (id_perfil_usuario, clave_operacion),
  ADD KEY idx_ayuda_contexto (id_perfil_usuario, id_ejecucion_actividad, id_paso_actividad, fecha_solicitud);

-- Las notificaciones de ayuda enlazan solo la solicitud; su ejecucion se deriva
-- desde solicitud_ayuda. id_ejecucion_actividad se reserva para ACTIVIDAD_COMPLETADA.
ALTER TABLE notificacion_cuidador
  ADD UNIQUE KEY uq_notif_solicitud (id_solicitud_ayuda),
  ADD UNIQUE KEY uq_notif_ejecucion (id_ejecucion_actividad);

SHOW CREATE TABLE solicitud_ayuda;
SHOW CREATE TABLE notificacion_cuidador;
-- No se modifica token_push, configuracion_cuidador ni tablas de autenticacion.
-- No hay backfill ni envio de notificaciones historicas.
-- Recuperacion: si falla el segundo ALTER, conservar el primero (es aditivo),
-- mantener backend detenido y revisar el error antes de continuar. No borrar datos.
-- Para volver al codigo anterior se pueden conservar columnas/indices adicionales.
-- Una retirada fisica futura de columnas requiere respaldo y aprobacion separada,
-- ya que perderia claves de idempotencia y contexto de las solicitudes nuevas.
