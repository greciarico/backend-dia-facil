# Primera agenda de la persona usuaria

`GET /api/usuario/agenda` requiere Bearer de dispositivo y `ROLE_USUARIO`.
No admite IDs de perfil/cuidador como autoridad. `AccesoDispositivoService`
resuelve nuevamente la credencial, su Dispositivo ACTIVO y su PerfilUsuario ACTIVO.
Una credencial pendiente o del cuidador no autoriza esta API. Respuesta sin caché.

```json
{
  "fecha": "2026-10-24",
  "zonaHoraria": "America/Lima",
  "actualizarEnSegundos": 3600,
  "perfil": { "idPerfilUsuario": 12, "nombre": "Persona", "avatar": null },
  "actividades": [
    { "idActividad": 7, "nombre": "Desayunar", "hora": "07:00", "codigoIcono": "bread", "estado": "PENDIENTE" }
  ]
}
```

`diafacil.agenda.zone=America/Lima` define el día de agenda, independientemente
de las zonas de autenticación y del reloj del móvil. La fecha se calcula una sola
vez por consulta. `actualizarEnSegundos` es la duración hasta la próxima medianoche
de esa zona (redondeada hacia arriba), para actualizar la pantalla sin polling.
La hora es LocalTime, sin fecha, UTC ni conversión al huso del teléfono.

El modelo no define calendario/recurrencia: en este incremento todas las
definiciones ACTIVA del perfil corresponden a cada día. Se ordenan por hora e ID.
La ejecución se une únicamente para esa fecha mediante LEFT JOIN con la fecha
en ON: ausencia = PENDIENTE, ejecución actual = EN_PROGRESO o COMPLETADA.
Una ejecución histórica nunca completa el día actual ni excluye la definición.
La API es de lectura: no crea ejecuciones ni progreso, ni modifica la consulta
del cuidador. Los futuros comandos de ejecución deberán respetar esta misma zona.

Se mantienen los errores E3: 401 VINCULACION_NO_VALIDA para credencial revocada,
dispositivo desvinculado o perfil no disponible; 403 para un rol pendiente;
503 para servicio no disponible. El frontend no convierte errores en agenda vacía.

## Detalle privado de actividad

`GET /api/usuario/actividades/{idActividad}` requiere ROLE_USUARIO y la misma
credencial E3. Perfil y Dispositivo se revalidan con `perfilActivo`; no se reciben
IDs de perfil/cuidador como autoridad. El servicio filtra la actividad ACTIVA
por ID y perfil ACTIVO del principal. Inexistente, ajena o inactiva: mismo
**404 ACTIVIDAD_NO_ENCONTRADA**, sin indicar pertenencia. No se amplía el DTO de
agenda ni se reutilizan endpoints del cuidador.

Respuesta 200, Cache-Control: no-store:

```json
{
  "fecha": "2026-10-24",
  "zonaHoraria": "America/Lima",
  "actualizarEnSegundos": 3600,
  "actividad": {
    "idActividad": 7,
    "nombre": "Desayunar",
    "hora": "07:00",
    "codigoIcono": "bread",
    "estado": "PENDIENTE",
    "guiaInicialCompletada": false
  }
}
```

Es un DTO proyectado, no una entidad: sin pasos/pictogramas, progreso ni relaciones
JPA. Comparte el cálculo temporal de agenda; consulta solo la ejecución del día
actual de Lima mediante LEFT JOIN con fecha en ON. La bandera es por actividad,
independiente del estado diario. GET es transaccional readOnly y no crea ni
modifica ejecuciones, actividades o ProgresoPaso. No hay endpoint de finalización
en este incremento. En un comando futuro deberá validarse nuevamente en backend
que la primera guía esté completada antes de permitir el modo DIRECTA.

## Salida local y reconexión

Salir del perfil pausa exclusivamente esta instalación. No desvincula ni reduce
el contador E1. La credencial deja de enviarse y queda retenida en SecureStore
para probar el vínculo anterior, sin almacenarse nombres ni códigos de conexión.
Una copia del secreto anterior sigue autorizando backend hasta que el cuidador
desvincule el dispositivo; salir localmente no es una revocación de seguridad.

Al reconectar se prepara y guarda una credencial pendiente nueva y se llama al
POST existente `/api/dispositivos/vincular`, añadiendo opcionalmente:

```json
{
  "codigoVinculacion": "DF-7KQ9W6H2M8V3X5RA",
  "nombreDispositivo": "Android",
  "credencialesPrevias": ["du_SECRETO_PREVIO_SOLO_EN_TRANSPORTE_HTTPS"]
}
```

Los secretos del ejemplo son placeholders, no credenciales válidas. No registrar
body, Authorization ni respuesta. Primero se valida/bloquea el perfil resuelto
por el código vigente. Una prueba previa solo permite reutilizar un Dispositivo
ACTIVO de **ese mismo perfil**. No reasigna un dispositivo a otro perfil. La
respuesta incluye `credencialDispositivo` únicamente al reutilizar el secreto
previo presentado por el cliente; se guarda como activo antes de confirmar éxito.
Se conserva el secreto original, evitando invalidar pruebas por reintentos
concurrentes. El servidor continúa almacenando únicamente su hash.

Sin prueba coincidente se crea una vinculación normal. La preparación nueva no
utilizada caduca sin Dispositivo. Ante respuesta perdida de reutilización, GET de
esa preparación sigue PENDIENTE: se conserva la prueba y se permite un reintento
explícito; nunca se repite POST automáticamente. Si se borran los datos seguros
de la aplicación, no puede reconocerse la instalación anterior y se considera
una instalación nueva. No se añade entidad de instalación ni esquema adicional.

DELETE `/api/dispositivos/vinculacion` queda limitado a cancelar preparaciones
(`ROLE_VINCULACION_PENDIENTE`). Backend también rechaza con 403 el intento sobre
un dispositivo ya vinculado después de bloquear/releer, protegiendo la carrera
entre cancelación y vinculación. La futura desvinculación corresponde a E2.

## Validación

Pruebas sin BD: contrato HTTP/roles/principal/no-store, reloj fijo alrededor de
medianoche, proyección de estados, validación actual de credencial, reutilización
segura y consultas HQL compiladas contra el modelo real. `AgendaUsuarioMySqlTest`
es opt-in con `DIAFACIL_TEST_MYSQL_*`, exclusivamente en `diafacil_test_*` existente,
sin DDL: comprueba historial, ausencia de ejecución, orden y filtros con rollback
de todas sus fixtures. No ejecutarlo sobre la BD de desarrollo.

Validación actual: Java 21 y JUnit Platform aislado, 272 pruebas correctas
y 8 pruebas MySQL omitidas por falta de configuración de pruebas. Se excluye
`BackendApplicationTests` (contexto/datasource completo). Se reutilizan las
dependencias locales porque el Maven Wrapper tiene el fallo PowerShell ya
documentado. No se ejecutaron consultas ni modificaciones sobre la BD local.
