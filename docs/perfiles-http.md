# Perfiles del cuidador autenticado

`GET /api/perfiles`, con `Authorization: Bearer <access>` de la sesión existente.
No admite un propietario proporcionado por el cliente: `idCuidador` se obtiene
del principal `SesionCuidadorService.Identidad`. Parámetros extra no cambian esa
identidad. El ID de cuenta, el refresh y tokens por query no autentican.

Respuesta 200 y `Cache-Control: no-store`:

```json
{
  "perfiles": [{"idPerfilUsuario": 12, "nombre": "Persona", "avatar": null}],
  "maximoPerfilesActivos": 4
}
```

Sin perfiles se devuelve `perfiles: []`, no 204. Solo se consultan filas ACTIVO
del propietario, ordenadas por nombre e ID. La proyección no carga actividades,
dispositivos ni configuración; no expone códigos de vinculación, propietario,
estado, fechas ni credenciales. No hay paginación ni truncamiento de resultados.

El límite procede de `PoliticaPerfiles.MAXIMO_ACTIVOS`. La consulta no crea ni
reactiva perfiles. El comando POST valida el máximo transaccionalmente; el trigger
SQL existente es una barrera adicional. Reactivación sigue fuera del alcance.

`avatar` conserva VARCHAR(255) nullable del modelo, distinto de avatarId de la
cuenta del cuidador. La lectura frontend reconoce IDs locales conocidos y URLs
públicas HTTPS; null, formatos desconocidos y fallos de imagen muestran inicial.
Las nuevas creaciones solo escriben IDs del catálogo local o null. Las URLs se
conservan únicamente como compatibilidad de lectura. No hay subida de fotos ni Cloudinary.

Errores ProblemDetail existentes: 401 SESION_REQUIERE_RENOVACION para access
vencido/ausente, 401 SESION_INVALIDA para sesión revocada/expirada o cuenta inactiva,
403 ACCESO_DENEGADO, 503 SERVICIO_NO_DISPONIBLE y 500 ERROR_INTERNO. GET y POST exactos
requieren ROLE_CUIDADOR, al igual que el GET de detalle. Otros métodos/módulos siguen denegados.
No hay cookies, HttpSession ni uso de dispositivo.

Validación aislada: controller/principal, servicio y bootstrap real JPA con
proyección JPQL y parámetros, sin JDBC. La comprobación con filas reales de dos
cuidadores y perfiles activos/inactivos se realiza en la base de prueba existente;
no se siembran datos ni se ejecutan SQL desde este incremento.

## Crear PerfilUsuario

`POST /api/perfiles`, `Content-Type: application/json`, Bearer de la sesión existente.

```json
{"nombre":"Grecia","avatar":"person-01"}
```

`nombre` obligatorio, se eliminan espacios exteriores antes de validar máximo 60.
No hay restricciones nuevas sobre caracteres ni unicidad de nombre. `avatar` es
opcional/nullable: solo person-01–person-10 y animal-01–animal-10, según el catálogo
compartido. Strings vacíos, URLs y otros IDs se rechazan. Propietario, estado y
código de vinculación no son argumentos del comando, aunque se envíen en JSON.

201 Created, no-store, DTO mínimo:

```json
{"idPerfilUsuario":12,"nombre":"Grecia","avatar":"person-01"}
```

Se conserva el contrato de creación sin Location. No se devuelven código interno,
configuración ni entidad JPA. El código obligatorio se genera con SecureRandom,
formato DF- y seis dígitos, estable y único; no se muestra ni implementa vinculación.

La transacción REQUIRES_NEW/READ_COMMITTED bloquea la fila del cuidador mediante
PESSIMISTIC_WRITE y refresca su estado antes de contar ACTIVO. Con cuatro rechaza
409 LIMITE_PERFILES_ALCANZADO; sin cupo no inserta. Inserta perfil ACTIVO y su
ConfiguracionPerfil en el mismo commit: MEDIANO, alto contraste false, lectura voz
true, recordatorio 10. Fecha de renovación null, timestamps de BD.

Solo una colisión de uq_perfil_codigo reintenta, hasta tres transacciones completas
independientes, tras rollback. La señal SQL conocida del trigger se traduce al
mismo 409. Otros errores no se reinterpretan como colisiones. Futuros comandos
de reactivación deben compartir el bloqueo del cuidador y la misma regla.

400 SOLICITUD_INVALIDA incluye errores con campo nombre/avatar y mensaje seguro.
409 indica límite. 503/500 y errores de autenticación mantienen ProblemDetail.
Solo POST exacto se añade a la exclusión CSRF: sigue protegido por Bearer, no es público.

No hay idempotencia persistente: si se pierde una respuesta, el cliente no repite
por red/timeout/5xx y pide revisar Mis perfiles. La única repetición automática
permitida es un 401 renovable que rechazó el comando antes de crear, tras refresh.

## Consultar un perfil

`GET /api/perfiles/{idPerfilUsuario}`, con Bearer. Devuelve 200, no-store y el
mismo DTO mínimo: `idPerfilUsuario`, `nombre`, `avatar`.

La consulta aplica conjuntamente ID, cuidador del principal y estado ACTIVO.
Un perfil inexistente, ajeno o inactivo devuelve el mismo 404
`PERFIL_NO_ENCONTRADO`, sin revelar su existencia. Un ID no positivo también
devuelve 404; un formato incompatible con Integer devuelve 400.
Mantiene los errores de autenticación existentes y no modifica sesión, SQL,
GET de colección ni POST. No expone la entidad JPA ni códigos de vinculación.

## Consultar actividades del perfil

`GET /api/perfiles/{idPerfilUsuario}/actividades`, con Bearer de cuidador.
200 y `Cache-Control: no-store`:

```json
{"actividades":[]}
```

Si hay datos, cada elemento contiene únicamente `idActividad` y `nombre`:

```json
{"actividades":[{"idActividad":7,"nombre":"Desayunar"}]}
```

Antes de leer actividades se comprueba el perfil mediante el detalle existente:
ID, propietario del principal y ACTIVO. La consulta proyectada de actividades
restringe también esos datos y estado ACTIVA; orden estable por ID. No acepta
idCuidador del cliente como autoridad. Ajeno, inactivo e inexistente conservan
404 PERFIL_NO_ENCONTRADO, nunca una lista vacía. IDs no positivos siguen el mismo
404; formato no entero, 400. Sesión y errores de infraestructura conservan el
contrato existente. Solo este GET se habilita; no se añade POST de actividades.

La lista representa definiciones activas, sin filtro por fecha. No consulta
EjecucionActividad ni expone hora, pictogramas, pasos, relaciones internas o progreso.
Esos campos/capacidades se incorporarán en sus incrementos correspondientes.
La UI actual presenta el vacío solo si la lista real está vacía; una lista con
datos tiene un aviso neutral, sin simular agenda ni descartarla como vacío.
