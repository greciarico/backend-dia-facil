# DF-09 · Edición de una actividad

El cuidador consulta y actualiza el recurso privado con GET y PUT en /api/perfiles/{idPerfilUsuario}/actividades/{idActividad}. Ambos devuelven idActividad, revision, nombre, hora, codigoIcono, estadoHoy y los pasos ordenados con idPasoActividad, descripcion y pictograma. El PUT requiere revision, nombre, hora, codigoIcono y pasos. Cada paso lleva descripcion e idArasaac opcional. Para mantener un pictograma local histórico, enviar conservarPictogramaDePaso con el ID de ese paso en la versión vigente, sin idArasaac.

Un perfil o actividad ajenos o inactivos devuelven 404. Una revisión obsoleta devuelve 409 ACTIVIDAD_MODIFICADA. La actualización comparte la validación de creación y comprueba el estado de hoy con el reloj de la agenda dentro de la transacción. El listado del cuidador refleja inmediatamente la definición vigente.

La actividad conserva su identidad. Una ejecución fija la revisión al comenzar. La agenda, guía, ayuda y notificaciones de esa ejecución usan dicha revisión. La primera guía completada permanece acreditada para la actividad. El cambio de hora no requiere reprogramar recordatorios, pues el proyecto todavía no los programa por actividad.

## Transición de esquema

Con el backend detenido y una copia de seguridad verificada, revisar y ejecutar primero las dos consultas de auditoría al inicio de [la transición SQL](sql/add-versiones-actividad.sql). Deben devolver cero filas antes de continuar con el DDL. Validar previamente en una copia desechable. Aplicar una sola vez. El script crea la versión 1 a partir de la definición disponible, preserva IDs de pasos y progreso, y agrega claves externas para garantizar la integridad de la versión fijada. No recupera definiciones que nunca se almacenaron. El DDL de MySQL puede confirmarse por etapas. Si falla, detener el despliegue y restaurar la copia o revisar el estado de cada etapa antes de continuar.

El código y el esquema se despliegan juntos. Hibernate update por sí solo no rellena la tabla histórica ni instala las claves compuestas de este script. En una base nueva, Hibernate crea el modelo y la creación registra la versión 1.
