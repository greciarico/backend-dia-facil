# Vinculación de dispositivos — E1 (cuidador)

E1 permite consultar y renovar el código estable de un perfil y contar sus dispositivos ACTIVO. No vincula ni desvincula dispositivos. `dispositivo` pertenece al PerfilUsuario; no guarda sesiones del cuidador y no se relaciona con `sesion_cuidador`.

## HTTP privado

Ambas rutas requieren Bearer del cuidador. La identidad proviene exclusivamente del principal de Spring Security; nunca de un parámetro `idCuidador`. Perfil inexistente, ajeno o INACTIVO: **404 `PERFIL_NO_ENCONTRADO`**, sin revelar su existencia. Respuestas exitosas: `Cache-Control: no-store`.

`GET /api/perfiles/{idPerfilUsuario}/vinculacion`

```json
{
  "nombrePerfil": "Persona de ejemplo",
  "codigoVinculacion": "DF-7KQ9W6H2M8V3X5RA",
  "dispositivosActivos": 0,
  "renovacionEnSegundos": 0
}
```

Cuenta únicamente dispositivos ACTIVO mediante el índice existente perfil/estado. GET nunca genera ni renueva códigos. El DTO general de perfiles no expone el código.

`POST /api/perfiles/{idPerfilUsuario}/vinculacion/renovar-codigo`

```json
{ "codigoActual": "DF-7KQ9W6H2M8V3X5RA" }
```

Devuelve 200 con la misma estructura de consulta y el nuevo código. `codigoActual` es una precondición de concurrencia, no una identidad ni autoridad de acceso. Se compara exactamente después de bloquear y releer el perfil ACTIVO del propietario. Si ya cambió: **409 `CODIGO_VINCULACION_CAMBIADO`**, sin otra renovación. Body vacío/inválido: 400 `SOLICITUD_INVALIDA`. Colisiones aleatorias agotadas: 503 `SERVICIO_NO_DISPONIBLE`.

La transacción REQUIRES_NEW / READ_COMMITTED usa PESSIMISTIC_WRITE sobre el perfil, reemplaza código y fecha de renovación (hora de BD) y hace flush antes de responder. Una colisión de `uq_perfil_codigo` revierte toda la transacción antes de reintentar, máximo tres intentos. No se reintentan otros fallos. No modifica dispositivos ni revoca accesos ya vinculados.

### Cooldown entre renovaciones

`PoliticaVinculacion` impone **60 segundos** entre renovaciones, independientemente del OTP. `fecha_renovacion_codigo` ya existente conserva la última renovación; NULL permite la primera inmediatamente. No se cambia el esquema ni se modifican códigos legacy automáticamente.

Después de comprobar ownership, perfil ACTIVO y precondición de código bajo bloqueo, se obtiene `local datetime` de la BD y se compara con la última renovación + 60 s. Si todavía falta tiempo: **429 `RENOVACION_PREMATURA`**, `Retry-After: N` y extensión `renovacionEnSegundos: N` en ProblemDetail. No se genera ni escribe otro código. En el límite de 60 segundos se permite renovar. Un código esperado obsoleto continúa devolviendo 409 antes de evaluar el cooldown.

GET y POST devuelven `renovacionEnSegundos`, calculado usando hora de BD y redondeado hacia arriba, sin valores negativos. POST exitoso devuelve 60; GET devuelve el tiempo restante o cero. El frontend no envía fecha/hora ni determina esta regla. El reloj del teléfono no participa en la autorización.

## Formato y compatibilidad

**Actualización E3:** el flujo público y el frontend de esta entrega admiten únicamente el formato vigente. La compatibilidad legacy descrita a continuación corresponde a E1 antes de E3. Completar la [auditoría/transición explícita y activación](dispositivos-e3.md) antes de probar esta entrega. No hay renovación automática durante GET.

Los perfiles nuevos y las renovaciones usan `DF-` + 16 símbolos aleatorios de `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` (32 símbolos, 80 bits). Cada posición utiliza SecureRandom.nextInt(32). Longitud total 19, compatible con VARCHAR(20) y UNIQUE `uq_perfil_codigo` actuales. Sin migración de esquema.

Históricamente E1 conservó códigos `DF-` + seis dígitos hasta renovación explícita.
La entrega E3 exige su transición controlada antes de activarse; no mantener
compatibilidad legacy en el flujo público. E1 no migra ni renueva durante GET.
Ver el procedimiento de auditoría/transición de E3 para una BD histórica.

El QR se genera localmente y codifica exactamente la misma cadena visible/copiable. Sin IDs, correo, datos personales, access/refresh tokens ni URL. No se almacena una imagen QR. El código vive únicamente en memoria del frontend y portapapeles cuando el cuidador pulsa Copiar; no se registra ni se persiste en SecureStore ni en parámetros de navegación.

## Frontend y resultado desconocido

El cliente privado conserva refresh y restauración de sesión existentes. El POST solo admite el replay ya existente de un 401 renovable rechazado antes de ejecutar el comando, nunca por timeout, red o 5xx.

Mientras renueva se ocultan QR/código y se deshabilita copia. Si falla la respuesta o llega 409, se consulta por GET el código actual. Si también falla GET, el código permanece oculto y Reintentar ejecuta solo GET. No se afirma que la renovación falló ni se muestra un código posiblemente invalidado. Se ignoran respuestas tras blur/cambio de perfil/sesión. Se consulta al enfocar y al volver a primer plano, sin polling.

Ante 429 también se consulta el estado actual por GET y se representa la espera recibida. Si esa consulta falla, no se inventa una duración ni se repite POST. El contador local usa un instante objetivo monotónico basado en la duración de respuesta; es una estimación conservadora por la latencia y el redondeo. El backend sigue siendo la autoridad. La acción permanece deshabilitada hasta cero; el intervalo únicamente actualiza la UI, no consulta la API. Se elimina al perder foco y no publica tras logout/cambio de sesión.

La cantidad de dispositivos es real; el acceso a su gestión permanece deshabilitado hasta E2. Atrás vuelve al hub del mismo perfil. La confirmación de renovación explica que los dispositivos ya vinculados seguirán funcionando.

## Fuera de E1

- E2: listado y desvinculación; sin pantallas provisionales.
- E3 se implementó posteriormente: [contrato y seguridad actuales](dispositivos-e3.md),
  con límites por origen/globales, errores genéricos y credenciales independientes.
  Valida otra vez código/perfil bajo el mismo bloqueo de perfil que la renovación.
- No reutilizar OTP / `codigo_acceso` ni sesiones del cuidador para el acceso del dispositivo.

## Validación

Pruebas unitarias de códigos, ownership, código obsoleto, conteo ACTIVO, bloqueo/relectura, hora de BD, rollback/reintento limitado; MockMvc verifica Bearer, no-store, DTO, 404 y 409. Consultas Hibernate verificadas contra el modelo sin JDBC.

`VinculacionMySqlTest` comprueba dos renovaciones concurrentes, una sola ganadora y conservación de dispositivos. Se ejecuta solo con `DIAFACIL_TEST_MYSQL_URL` apuntando a `diafacil_test_*` y credenciales de esa BD desechable ya preparada. No aplica DDL, no usa configuración local y elimina únicamente sus propias fixtures.
