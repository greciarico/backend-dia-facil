# Contrato HTTP de autenticación del cuidador

Activación: `diafacil.auth.enabled=true`. El Controller usa el Service existente;
no consulta repositories ni abre transacciones. Sin activación no se registra.

## Solicitar código

`POST /api/auth/cuidadores/solicitar-codigo`, `Content-Type: application/json`.

```json
{"email":"grecia@example.com"}
```

Correo obligatorio, válido y de hasta 150 caracteres. Se recortan espacios
exteriores; la normalización de minúsculas sigue correspondiendo al Service.

Respuesta 200, `application/json`:

```json
{"estado":"CODIGO_SOLICITADO","mensaje":"Revisa tu correo para encontrar el código.","reenvioEnSegundos":45}
```

La llamada termina después de persistir y ejecutar el envío SMTP. La respuesta
no revela si existe una cuenta ni garantiza entrega en la bandeja de entrada.
El OTP tiene vigencia de diez minutos desde su emisión.

El cooldown oficial de reenvío es de 45 segundos desde la emisión anterior.
`PoliticaCodigoAcceso.REENVIO_EN_SEGUNDOS` centraliza la regla y su representación
HTTP. El frontend usa `reenvioEnSegundos` para una espera local conservadora desde
la recepción, no como tiempo restante exacto del servidor.

## Verificar código

`POST /api/auth/cuidadores/verificar-codigo`, `Content-Type: application/json`.

```json
{"email":"grecia@example.com","codigo":"000123"}
```

Las mismas restricciones de correo y un código String de exactamente seis
dígitos ASCII. No se recortan espacios del código; se conservan ceros iniciales.
Un cuerpo inválido devuelve 400 sin llamar al Service ni consumir intentos.
Un código de seis dígitos incorrecto sí pasa al Service e incrementa intentos.

Respuesta 200 para cuidador ACTIVO:

```json
{"estado":"AUTENTICADO","idCuidador":3,"cuidador":{"idCuidador":3,"nombre":"Grecia","avatarId":null},"sesion":{"tokenType":"Bearer","accessToken":"<opaco>","accessExpiresAt":"<UTC>","refreshToken":"<opaco>","refreshExpiresAt":"<UTC>","sessionExpiresAt":"<UTC>"}}
```

Respuesta 200 para correo sin cuenta:

```json
{"estado":"REGISTRO_PENDIENTE","comprobanteRegistro":"<comprobante temporal firmado>"}
```

Los campos que no corresponden se omiten. No se crea un cuidador incompleto.
`AUTENTICADO` verifica OTP y emite credenciales de una nueva sesión independiente.
`REGISTRO_PENDIENTE` no emite sesión. El comprobante autoriza solo el registro posterior y
vence a los diez minutos de ser emitido. No registrar cuerpos, códigos o
comprobantes en logs.

## Completar registro

`POST /api/auth/cuidadores/completar-registro`, `Content-Type: application/json`.

```json
{"comprobanteRegistro":"<comprobante temporal firmado>","nombre":"Grecia","avatarId":"person-01"}
```

Comprobante obligatorio de hasta 2048 caracteres. Nombre obligatorio, recortado
en extremos y de hasta 100 caracteres. `avatarId` es opcional/nullable: solo se
admiten los 20 IDs centralizados en `CatalogoAvatarCuidador` (`person-01` a
`person-10`, `animal-01` a `animal-10`). Los assets existen solo en frontend.
No se admite correo ni ID de cuidador como autoridad: la cuenta usa el correo
del comprobante firmado. El controller delega al servicio existente.

Respuesta 201, con sesión opaca, sin JWT ni cookies:

```json
{"estado":"REGISTRO_COMPLETADO","idCuidador":3,"cuidador":{"idCuidador":3,"nombre":"Grecia","avatarId":"person-01"},"sesion":{"tokenType":"Bearer","accessToken":"<opaco>","accessExpiresAt":"<UTC>","refreshToken":"<opaco>","refreshExpiresAt":"<UTC>","sessionExpiresAt":"<UTC>"}}
```

Se crea una cuenta ACTIVA con nombre, avatar opcional y sesión en la misma transacción.
No se crea un PerfilUsuario. El comprobante debe estar vigente, firmado para
registro y respaldado por un OTP consumido del mismo correo. Repetir el registro
devuelve CUENTA_EXISTENTE, sin editar la cuenta. Una respuesta perdida puede
haber creado la cuenta; no tratar un 409 como sesión ni éxito ficticio.

Personalizar más tarde no omite el nombre: envía `avatarId: null`.
El frontend representa el avatar con la inicial del nombre, sin imagen default.
La personalización de foto y la navegación posterior están fuera del contrato.

## Sesión persistente

Access y refresh son opacos aleatorios: `a_` o `r_` más 43 caracteres Base64URL
(32 bytes aleatorios). Solo se guardan hashes SHA-256 BINARY(32) en backend.
Access dura 15 minutos, acotado por los vencimientos de sesión. Inactividad:
30 días sin renovar. Máximo absoluto: 90 días desde su creación, nunca reiniciado
por refresh. Fechas ISO UTC. Políticas: diafacil.auth.session.access/idle/absolute.

`POST /api/auth/cuidadores/renovar-sesion`, body `{"refreshToken":"<opaco>"}`.
HTTP 200: `{ "cuidador": {"idCuidador":3,"nombre":"Grecia","avatarId":null},
"sesion": { ...campos anteriores... } }`. Cuenta debe seguir ACTIVA.
Rotación bajo bloqueo transaccional de la familia: consume el refresh y crea
otra generación aleatoria. Access anteriores siguen vigentes hasta su expiración
si la sesión sigue activa. Reutilización de refresh consumido revoca solo su sesión
y retorna 401 SESION_INVALIDA después de confirmar revocación en BD.
No hay operacionId, HMAC adicional, confirmación ni recuperación de respuesta.
Si se pierde el nuevo refresh, puede requerirse autenticar otra vez mediante OTP.

`POST /api/auth/cuidadores/cerrar-sesion`, mismo body. HTTP 204 idempotente.
Un refresh reconocido, incluso consumido, revoca su familia; desconocido devuelve
204. No elimina cuenta ni revoca otras sesiones. Sin cuerpo, tokens ni cookies.

`GET /api/auth/cuidadores/sesion`: solo `Authorization: Bearer <access>`.
Devuelve datos del cuidador obtenidos del principal Spring, sin credenciales.
Nunca toma identidad de un idCuidador en body, query o URL. No acepta tokens por URL.

Access ausente, inválido o vencido: 401 SESION_REQUIERE_RENOVACION.
Familia vencida/revocada o cuenta inactiva: 401 SESION_INVALIDA.
La UI solo renueva el primer caso; no reintenta 403 ni fallos de red automáticamente.
Todas las respuestas conservan no-store. HTTPS es obligatorio fuera de desarrollo LAN.

La infraestructura sesion_cuidador y credencial_sesion_cuidador es independiente
de dispositivo y token_push. Conserva historial de hashes hasta que la familia
finaliza y lo limpia después de siete días mediante tarea diaria.

## Errores

Formato `application/problem+json`; el frontend decide por `codigo`, no por
`detail`, que es un mensaje de presentación. No se exponen valores rechazados,
excepciones, causas, mensajes SQL/SMTP, hashes o secretos.

```json
{"type":"about:blank","title":"Unprocessable Content","status":422,"detail":"El código ingresado no es correcto.","instance":"/api/auth/cuidadores/verificar-codigo","codigo":"CODIGO_INCORRECTO"}
```

| Caso | HTTP | codigo |
|---|---:|---|
| Entrada inválida o JSON ilegible | 400 | SOLICITUD_INVALIDA |
| Tipo de contenido no soportado | 415 | SOLICITUD_INVALIDA |
| Código inexistente | 422 | CODIGO_INEXISTENTE |
| Código incorrecto | 422 | CODIGO_INCORRECTO |
| Código vencido | 422 | CODIGO_VENCIDO |
| Código usado | 422 | CODIGO_USADO |
| Límite de cinco errores | 429 | LIMITE_INTENTOS |
| Cuenta inactiva | 403 | CUENTA_INACTIVA |
| Comprobante manipulado, vencido o sin respaldo válido | 422 | COMPROBANTE_INVALIDO |
| Registro repetido o correo ya registrado | 409 | CUENTA_EXISTENTE |
| Reenvío prematuro | 429 | REENVIO_PREMATURO |
| Fallo SMTP o indisponibilidad identificada de persistencia | 503 | SERVICIO_NO_DISPONIBLE |
| Fallo inesperado o de preparación del correo | 500 | ERROR_INTERNO |
| Recurso bloqueado por seguridad | 403 | ACCESO_DENEGADO |
| Access requiere renovación | 401 | SESION_REQUIERE_RENOVACION |
| Refresh/familia inválida, revocada o vencida | 401 | SESION_INVALIDA |

En validación se añade `errores`, una lista de objetos `campo` y `mensaje`.
Nunca se incluye el valor introducido. En REENVIO_PREMATURO se devuelve
`Retry-After: 45`: espera conservadora desde el rechazo, no tiempo restante
exacto. LIMITE_INTENTOS no devuelve ese header: se necesita otro código, el
actual no vuelve a funcionar por esperar.

Todas las respuestas de autenticación y errores incluyen `Cache-Control` con
`no-store`. No se crean cookies ni HttpSession: las sesiones del cuidador usan credenciales Bearer opacas y BD.

## Seguridad y clientes

Solo los cinco POST exactos documentados son públicos y están excluidos de CSRF.
GET sesion y GET /api/perfiles requieren ROLE_CUIDADOR obtenido por Bearer.
La consulta de perfiles se documenta en perfiles-http.md. Las demás rutas se deniegan.
No hay login por formulario, HTTP Basic, JWT ni autenticación por contraseña.
Cerrar-sesion es explícito; no se habilita el logout por formulario de Spring.
La protección CSRF sigue habilitada fuera de esos cinco POST.

Validación local mediante AuthenticationFilter, ProviderManager y AuthenticationProvider
de Spring Security ya instalado; el provider recibe hashes y construye Identidad.
Un nuevo endpoint privado debe registrarse en el matcher Bearer y en autorización,
y resolver pertenencia desde ese principal. No se integra servidor OAuth externo.

Los clientes iniciales serán Android/iOS. No se habilita CORS ni comodines.
Antes de conectar Web se definirán sus orígenes concretos. No crear políticas
permisivas para resolver errores del navegador.

## Validación

`AutenticacionCuidadorControllerTest` y `SecurityConfigurationTest` usan
`@WebMvcTest` con MockMvc, Service simulado y filtros de seguridad reales.
Comprueban el contrato, validación, errores seguros y límites de acceso.
No desactivar filtros ni enviar correos reales para estas pruebas; no requieren
JDBC. Ejecutarlas junto con las pruebas existentes y excluir `contextLoads`
cuando la validación deba permanecer aislada de MySQL.
