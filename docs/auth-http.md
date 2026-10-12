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
{"estado":"CODIGO_SOLICITADO","mensaje":"Revisa tu correo para encontrar el código.","reenvioEnSegundos":60}
```

La llamada termina después de persistir y ejecutar el envío SMTP. La respuesta
no revela si existe una cuenta ni garantiza entrega en la bandeja de entrada.
El OTP tiene vigencia de diez minutos desde su emisión.

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
{"estado":"AUTENTICADO","idCuidador":3}
```

Respuesta 200 para correo sin cuenta:

```json
{"estado":"REGISTRO_PENDIENTE","comprobanteRegistro":"<comprobante temporal firmado>"}
```

Los campos que no corresponden se omiten. No se crea un cuidador incompleto.
`AUTENTICADO` representa la verificación del OTP: todavía no entrega una sesión
ni habilita otras rutas. El comprobante autoriza solo el registro posterior y
vence a los diez minutos de ser emitido. Completar registro no tiene endpoint
en esta etapa. No registrar cuerpos, códigos o comprobantes en logs.

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
| Reenvío prematuro | 429 | REENVIO_PREMATURO |
| Fallo SMTP o indisponibilidad identificada de persistencia | 503 | SERVICIO_NO_DISPONIBLE |
| Fallo inesperado o de preparación del correo | 500 | ERROR_INTERNO |
| Recurso bloqueado por seguridad | 403 | ACCESO_DENEGADO |

En validación se añade `errores`, una lista de objetos `campo` y `mensaje`.
Nunca se incluye el valor introducido. En REENVIO_PREMATURO se devuelve
`Retry-After: 60`: espera conservadora desde el rechazo, no tiempo restante
exacto. LIMITE_INTENTOS no devuelve ese header: se necesita otro código, el
actual no vuelve a funcionar por esperar.

Todas las respuestas de autenticación y errores incluyen `Cache-Control` con
`no-store`. Las dos operaciones de autenticación no crean sesión ni cookies de autenticación.

## Seguridad y clientes

Solo los dos POST exactos son públicos y están excluidos de CSRF. El resto se
deniega incluso con credenciales Basic o un usuario autenticado. No hay login
por formulario, HTTP Basic, logout, JWT ni nuevas credenciales de usuario.
La protección CSRF sigue habilitada fuera de esos dos POST.

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
