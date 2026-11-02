# Refactorización conservadora de notificaciones

## Alcance aplicado

Esta entrega mantiene los endpoints, DTO, generaciones de tokens, reglas de ayuda, estados SQL y política de entrega. No modifica entidades JPA, MySQL, Firebase, EAS, credenciales, dependencias ni la configuración privada local. No incorpora las optimizaciones de consultas ni de reconciliación identificadas en la auditoría.

Los cambios de código son:

- El listado y marcado de lectura pasan `expectedRevision` al transporte privado, además de comprobar la revisión al recibir la respuesta.
- La pantalla del historial conserva una intención push incompleta al perder foco. Al regresar, vuelve a obtener el detalle autorizado. No repite una intención ya resaltada o cancelada por un cambio de filtro.
- Una búsqueda conserva su presupuesto automático de cinco páginas al recuperar foco. Un reintento explícito permite un nuevo presupuesto acotado. El resaltado continúa durando tres segundos desde la visibilidad.
- El hook de ayuda conserva la operación pendiente y su clave, pero solo mantiene un intervalo mientras existe una cuenta regresiva activa. Detiene el intervalo al vencer o perder foco y lo recupera si sigue siendo necesario.
- La campana conserva su callback personalizado y navegación predeterminada; se retiran ramas que nunca representaban un estado deshabilitado real.
- `PushProperties.toString()` oculta la credencial. `EntregaPushWorker.Trabajo.toString()` solo conserva el identificador y una marca de contenido reservado.
- Se corrige el literal de instalación del mensaje de conflicto de registro.
- Los archivos de ejemplo identifican las variables necesarias sin incorporar sus valores privados.

Las constantes operativas reciben nombres manteniendo sus valores: página de veinte notificaciones, cuarenta y cinco segundos por reconciliación, sesenta segundos de cooldown, lote de veinte trabajos, ocho intentos de envío, arrendamiento de dos minutos, quince minutos para receipts y vigencia inicial de veinticuatro horas. No cambia la secuencia de espera ni el jitter.

## Protección y revisión de Git

Backend y frontend son repositorios independientes. No se modifica su índice ni se crea un repositorio raíz. La revisión distingue cambios previamente presentes de esta refactorización.

Las reglas de cada repositorio protegen archivos `.env` y sus variantes y conservan excepciones para plantillas. El backend también protege una carpeta de credenciales, formatos de claves y configuraciones locales alternativas de su raíz; conserva `application-*.example.properties`. La plantilla pública de push mantiene el envío desactivado y referencia variables de entorno.

La configuración real `application-local.properties`, `.env.local` y `google-services.json` debe permanecer ignorada y sin rastrear. Que un archivo esté ignorado no retira una versión previamente rastreada; el estado se comprueba con `git ls-files` y `git check-ignore`.

Para revisar el versionado de los incrementos se deben distinguir estos grupos:

| Repositorio | Archivos relacionados que requieren revisión selectiva |
| --- | --- |
| Backend | `service/ayuda/`, `service/notificaciones/`, `service/push/`, sus controladores, entidades relacionadas, configuraciones de seguridad y pruebas específicas. Las rutas Java parten de `src/main/java/dia_facil/backend/`. |
| Backend | `docs/ayuda-notificaciones-http.md`, `docs/push-android-http.md`, `docs/push-android-validacion.md`, scripts SQL de referencia y este documento. Los scripts no deben ejecutarse por versionarlos. |
| Frontend | `src/context/carer-push.tsx`, servicios de ayuda y notificaciones, hooks relacionados, DTO e intención push, pantallas Apoyo/Notificaciones, cambios de Mi perfil y navegación, modal/tarjeta/campana y pruebas específicas. |
| Frontend | `app.config.ts`, `eas.json`, recursos de notificación e ilustración, documentación y cambios de manifiestos de dependencias ya existentes del incremento. No se actualizan versiones en esta refactorización. |

No se debe incorporar indiscriminadamente todo el árbol de trabajo. Los cambios de accesibilidad, perfiles y otros módulos de integrantes del equipo se conservan. El documento `documentacion-dia-facil/jhonatan-notificacion.md` está fuera de estos dos repositorios; esta entrega no decide su política de versionado ni crea otra raíz Git.

## B1: activación tardía frente a desactivación

### Estado y causa

La corrección está implementada y la tabla aditiva ya fue creada en el entorno local mediante el SQL autorizado, tras verificar respaldo y restauración. La validación completa JPA y el primer arranque con perfil local, validate y push desactivado fueron correctos. Metro sirve el JavaScript actualizado. El responsable ya arrancó push con su token privado; se verificaron perfil local, validate y diafacil.push.enabled=true en el proceso que escucha en 8080. Las pruebas reales Android siguen pendientes y no se consideran completadas por las pruebas automatizadas.

Una cancelación o timeout HTTP del cliente no confirma un rollback remoto. Antes del cambio, PUT podía terminar después de DELETE/GET y reactivar el token. DELETE sin token no conservaba ninguna marca. Los bloqueos de sesión y token por sí solos no identificaban una intención anterior.

### Contrato condicional y persistencia

- GET /api/push/registro devuelve el DTO autorizado existente y ETag fuerte opaco, ligado a instalación, sesión y versión. No crea filas.
- PUT y DELETE exigen un único If-Match. Ausente: 428; formato inválido: 400; versión anterior: 412. No se aceptan comodines ni etiquetas débiles.
- Una escritura aceptada devuelve el nuevo ETag; GET/PUT mantienen 200 y DELETE 204. Se conserva Cache-Control: no-store.
- control_registro_push tiene solo id_instalacion VARCHAR(36) como PK y version BIGINT NOT NULL DEFAULT 0. No almacena credenciales ni duplica propietarios o preferencias.
- Cada escritura bloquea sesión, control y token, en ese orden. Un upsert transaccional cubre el primer comando sin fila previa. Comprobar If-Match, cambiar el token y avanzar versión constituye una única transacción.
- Todo PUT/DELETE aceptado avanza versión, incluso DELETE sin token o sobre un registro ya inactivo. DELETE ajeno nunca modifica el token de otra sesión.
- La generación de TokenPush conserva su significado para las entregas. Un avance exclusivo de la versión de comandos no la incrementa.
- La marca de control permanece aunque un token cambie de instalación o desaparezca. Eliminarla sin una estrategia adicional permitiría reutilizar versiones iniciales antiguas.
- No se necesita Idempotency-Key ni un historial de respuestas: repetir una precondición consumida da 412 sin reaplicar una intención anterior. Los reintentos técnicos del controlador conservan la cabecera original.

Si DELETE gana, el PUT antiguo queda obsoleto. Si PUT gana, DELETE obtiene 412 y la intención vigente de desactivar puede consultar y presentar una nueva escritura condicional. Tras confirmar DELETE, las activaciones basadas en versiones anteriores ya no son aceptables.

### Preferencia e intención del frontend

La clave SecureStore sigue separada por cuidador e instalación. Su valor pasa de una cadena a un objeto versionado:

```ts
{ version: 1, intentId: 'UUID de intención', preference: 'enabled' | 'disabled' | 'disable-pending' }
```

Las cadenas existentes se convierten conservando su preferencia. Una acción explícita crea una intención; la reconciliación automática la conserva. Las lecturas/escrituras de almacenamiento se serializan y las confirmaciones comprueban el identificador esperado. La revisión de sesión y la época de la elección invalidan continuaciones antiguas después de cada espera.

El proveedor obtiene ETag antes de la escritura. Un 412 permite una sola reconciliación adicional dentro del presupuesto existente, exclusivamente mientras sesión e intención siguen vigentes. El transporte privado no obtiene versiones nuevas por su cuenta.

Desactivar siempre establece una barrera mediante DELETE, también cuando GET informa inactivo y al recuperar preferencias antiguas disabled. Solo después del acuse y la comprobación remota se guarda disabled. Timeout, fallo de almacenamiento o falta de conexión mantienen la desactivación pendiente y evitan nuevas activaciones automáticas dentro de la intención actual. No se promete desactivación remota inmediata sin conexión.

### Compatibilidad y puesta en servicio

Archivos principales: ControlRegistroPush.java, RegistroPushService.java, RegistroPushController.java, frontend src/services/carer-push.ts y src/context/carer-push.tsx. No cambia la lógica del trabajador, la generación del destinatario, las solicitudes de ayuda, su cooldown, el progreso ni el historial interno.

docs/sql/add-control-registro-push.sql propone exclusivamente CREATE TABLE. No requiere backfill ni ALTER de tablas existentes. Hibernate también puede crear la entidad mediante update en desarrollo; no se debe arrancar contra MySQL real hasta revisar y autorizar ese cambio. Se conserva la estrategia de configuración del proyecto.

Las escrituras antiguas sin If-Match deben rechazarse; admitirlas permitiría reproducir B1. Coordinar el backend nuevo y la recarga de JavaScript, sin mantener instancias anteriores aceptando escrituras incondicionales. No hacen falta módulos nativos ni una APK nueva.

La revocación de sesión y las preferencias por cuenta permanecen separadas. El permiso Android no equivale a una preferencia habilitada ni a un registro remoto confirmado. Las notificaciones internas se conservan con push apagado. Una alerta previamente aceptada por Expo/FCM no puede retirarse retroactivamente.

### Comprobaciones focalizadas de B1

Resultados del alcance B1: 30 pruebas unitarias y de metadatos, 3 pruebas HTTP con MockMvc, 13 de integración push y 1 del esquema aditivo, todas sin fallos. Las pruebas de MySQL se ejecutaron en un servidor temporal independiente, en localhost:13379. El SQL propuesto y Hibernate produjeron las mismas columnas, tipos, nulabilidad, valores predeterminados y clave primaria. En esa fase de pruebas no se aplicó SQL a MySQL real ni se reinició el backend activo; la puesta en servicio posterior se registra al inicio de esta sección.

Las pruebas del proveedor cubren PUT tardío, ausencia de token, conflictos acotados, GET antiguo, respuesta DELETE perdida, almacenamiento, cuenta y sesión. Las pruebas HTTP usan el advice y la seguridad reales con servicios simulados. Las pruebas MySQL son opt-in y solo aceptan un esquema diafacil_test_* desechable; comprueban atomicidad, ambas órdenes de ejecución, reutilización de token, generación y persistencia con una nueva fábrica JPA. La prueba de esquema crea únicamente el control mediante Hibernate y exige que el segundo update no emita DDL.

## Comprobaciones focalizadas de esta entrega

Las pruebas de frontend cubren recuperación del foco, no repetición de intenciones consumidas, lectura por acción explícita, transporte ligado a sesión, persistencia de preferencias, intervalos y la regresión B1. Se mantienen las pruebas existentes de Mi perfil.

Las pruebas unitarias de backend comprueban las representaciones reservadas y mantienen las comprobaciones del cliente Expo, receipts, esperas y del controlador de registro. Se ejecutan en una copia temporal de código y recursos sin `application-local.properties`; no arrancan Spring Boot ni acceden a MySQL. Esta separación evita que Spring DevTools del backend activo observe una recompilación de su directorio de clases.

La recepción, el toggle y la navegación en los dos celulares requieren comprobación manual después de recargar el frontend. La fase de refactor no inició una build ni reinició Metro o Spring Boot; la puesta en servicio B1 posterior queda descrita en su apartado.

