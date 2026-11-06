# Ayuda y notificaciones internas ? Incremento 1

Requiere el SQL incremental aprobado por el responsable de base de datos antes de arrancar contra una base existente. No contiene push, tokens ni cola de entregas.

## Identidad y transporte

Endpoints de usuario: Bearer E3 vinculado; perfil derivado de la credencial. Endpoints de notificaciones: Bearer del cuidador. Sin cookies ni CSRF para los comandos exactos. Respuestas Cache-Control: no-store. Propietarios ajenos se ocultan con 404.

## Ayuda

POST /api/usuario/solicitudes-ayuda

Peticion JSON: {claveOperacion: string UUID, idActividad: integer|null, idEjecucion: integer|null, idPasoActividad: integer|null}.

General: omitir los tres IDs o enviarlos null. Contextual: enviar los tres. No acepta perfil/cuidador del cliente. Contexto exige actividad activa propia, ejecucion EN_PROGRESO de hoy America/Lima y paso activo alcanzado de esa actividad. Permite un paso anterior alcanzado, sin modificar progreso.

GET /api/usuario/solicitudes-ayuda/por-clave/{clave}

Ambos retornan HTTP 200 con exactamente:

~~~json
{"idSolicitudAyuda":1,"claveOperacion":"c3c155b2-f8b5-4dba-8cb1-47ab9c6df806","fechaSolicitud":"2026-11-01T07:00:00-05:00","estado":"PENDIENTE","idActividad":null,"idEjecucion":null,"idPasoActividad":null,"posicionPaso":null,"descripcionPaso":null}
~~~

Fechas DTO OffsetDateTime; persistencia DATETIME local de Lima. MySQL genera las fechas. Mantener la misma zona/reloj en servidor y conexion MySQL. Claves UUID canonizadas a minusculas. Una clave identifica una operacion por perfil: un reintento devuelve el registro original aun cambiando el cuerpo y aun dentro de cooldown. Recuperar por clave antes de crear otra clave si el resultado es incierto.

Cooldown 60 segundos por perfil/contexto (general o ejecucion+paso), serializado mediante bloqueo PESSIMISTIC_WRITE del perfil y aislamiento READ_COMMITTED. Se revalida la credencial tras adquirir el bloqueo. Solicitud y notificacion se persisten en la misma transaccion. El servicio no escribe progreso ni finaliza ejecuciones.

Errores application/problem+json con codigo: 400 SOLICITUD_INVALIDA; 404 SOLICITUD_NO_ENCONTRADA; 409 CONTEXTO_AYUDA_NO_VALIDO; 429 AYUDA_PREMATURA con Retry-After entero en segundos (redondeo hacia arriba). Errores de acceso E3 mantienen el contrato existente.

## Notificaciones

GET /api/notificaciones?categoria=TODAS|AYUDA|SISTEMA&cursor=...

Categoria por defecto TODAS. Respuesta exacta {notificaciones: Notificacion[], siguienteCursor: string|null}. Pagina de hasta 20 elementos, orden fechaCreacion DESC, idNotificacion DESC. Cursor opaco ligado a cuidador y categoria: enviarlo sin alteraciones y descartarlo al cambiar filtro. Cursor/categoria invalidos: 400 SOLICITUD_INVALIDA.

GET /api/notificaciones/{id}
PUT /api/notificaciones/{id}/leida (sin body)

Ambos retornan HTTP 200 con exactamente:

~~~json
{"idNotificacion":1,"tipo":"AYUDA_GENERAL","titulo":"Solicitud de ayuda","mensaje":"Grecia necesita tu ayuda","fechaCreacion":"2026-11-01T07:00:00-05:00","leida":false,"nombrePerfil":"Grecia","codigoIcono":null,"idSolicitudAyuda":1,"idEjecucion":null}
~~~

Tipos: AYUDA_GENERAL, AYUDA_CONTEXTUAL, ACTIVIDAD_COMPLETADA. AYUDA filtra los dos primeros; SISTEMA el tercero. El marcado es individual/idempotente y devuelve leida=true; no cambia estado PENDIENTE de la solicitud. No existe atencion de ayuda ni marcado masivo. 404 NOTIFICACION_NO_ENCONTRADA si falta o es ajena. Se conserva historia de perfiles inactivos. titulo y mensaje son historicos; nombrePerfil y codigoIcono se resuelven desde referencias actuales y el icono puede ser null.

AYUDA_CONTEXTUAL obtiene idEjecucion mediante solicitud; su FK directa en notificacion es NULL. Solo ACTIVIDAD_COMPLETADA ocupa esa FK unica. Asi se admiten multiples solicitudes de ayuda por ejecucion.

## Finalizacion

EjecucionUsuarioService crea ACTIVIDAD_COMPLETADA dentro de la transaccion al cambiar a COMPLETADA, tanto guiada (incluido cierre anticipado permitido) como directa. Sus retornos idempotentes no crean otra notificacion; no hay avisos por pasos ni creacion retroactiva para completadas anteriores.

## Esquema acordado

SolicitudAyuda: clave_operacion VARCHAR(36) nullable para legacy; posicion_paso INT nullable; descripcion_paso VARCHAR(120) nullable. UNIQUE uq_ayuda_perfil_operacion(id_perfil_usuario,clave_operacion). INDEX idx_ayuda_contexto(id_perfil_usuario,id_ejecucion_actividad,id_paso_actividad,fecha_solicitud).

NotificacionCuidador: UNIQUE uq_notif_solicitud(id_solicitud_ayuda), UNIQUE uq_notif_ejecucion(id_ejecucion_actividad). No modificar SQL automaticamente ni activar ddl-auto update contra una base sin migracion aprobada.

## Pruebas

Suites unitarias SolicitudAyudaServiceTest y NotificacionCuidadorServiceTest; suites MVC SolicitudAyudaControllerTest y NotificacionCuidadorControllerTest; regresiones EjecucionUsuarioServiceTest, EjecucionUsuarioControllerTest, SecurityConfigurationTest y PersistenceModelTest (proveedor que rechaza JDBC). Suite MySQL opt-in AyudaNotificacionesMySqlTest mantenida separadamente; solo base desechable diafacil_test_. Nunca usar credenciales de la base actual para estas pruebas.

## Cierre de validacion del Incremento 1

- AyudaNotificacionesMySqlTest pasa contra diafacil_test_ayuda_1793486773880: concurrencia, 60 segundos, idempotencia, progreso intacto, rollback atomico, finalizacion unica, filtros, paginacion y lectura propia.
- El montaje aislado utiliza HibernateJpaDialect y DELAYED_ACQUISITION_AND_HOLD para conservar READ_COMMITTED. El parser de catalogo separa la query antes de buscar la ultima barra; acepta serverTimezone=America/Lima.
- No se usa SpringBoot completo, SMTP ni DDL durante esta prueba. Sus datos se crean y limpian solo en el esquema desechable validado.
- MySQL actual diafacil: los dos ALTER TABLE siguen SIN aplicar. Nueva autorizacion expresa requerida. No arrancar local con ddl-auto=update mientras tanto.
- No generar historico retroactivo ni modificar token_push; push corresponde al Incremento 2.

Resultado final: frontend 234/234; backend aislado 323/323; integracion MySQL desechable 1/1. TypeScript correcto. SQL de diafacil NO aplicado; Android fisico pendiente.
