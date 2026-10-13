# Consulta autenticada de Mis perfiles

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
reactiva perfiles. El futuro comando debe validar el máximo transaccionalmente,
incluyendo concurrencia y reactivación; el trigger SQL existente de INSERT es
una barrera adicional y no sustituye esa validación. No se altera el esquema.

`avatar` conserva VARCHAR(255) nullable del modelo, distinto de avatarId de la
cuenta del cuidador. La lectura frontend reconoce IDs locales conocidos y URLs
públicas HTTPS; null, formatos desconocidos y fallos de imagen muestran inicial.
Esto no establece aún una política de escritura/subida de fotos ni Cloudinary.

Errores ProblemDetail existentes: 401 SESION_REQUIERE_RENOVACION para access
vencido/ausente, 401 SESION_INVALIDA para sesión revocada/expirada o cuenta inactiva,
403 ACCESO_DENEGADO, 503 SERVICIO_NO_DISPONIBLE y 500 ERROR_INTERNO. Solo GET exacto
queda autorizado para ROLE_CUIDADOR; POST, rutas de detalle y otros módulos
siguen denegados. No hay cookies, HttpSession ni uso de dispositivo.

Validación aislada: controller/principal, servicio y bootstrap real JPA con
proyección JPQL y parámetros, sin JDBC. La comprobación con filas reales de dos
cuidadores y perfiles activos/inactivos se realiza en la base de prueba existente;
no se siembran datos ni se ejecutan SQL desde este incremento.
