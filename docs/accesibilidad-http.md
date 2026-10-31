# Accesibilidad por perfil

Implementado el 30 de octubre de 2026. Reutiliza `ConfiguracionPerfil` 1:1 con
PK compartida; no introduce tablas, columnas, migraciones ni datos base nuevos.
La configuración pertenece al perfil, nunca a la sesión del cuidador o al móvil.

## Contrato

| Método/ruta | Identidad y finalidad |
| --- | --- |
| GET `/api/perfiles/{id}/accesibilidad` | ROLE_CUIDADOR, perfil ACTIVO propio |
| PATCH misma ruta | Actualización parcial de valores absolutos, misma autorización |
| POST `/api/perfiles/{id}/accesibilidad/restablecer` | Restablecimiento atómico de los tres campos |
| GET `/api/usuario/accesibilidad` | ROLE_USUARIO; `AccesoDispositivoService.perfilActivo` deriva el perfil del dispositivo ACTIVO |

Todas responden 200, `Cache-Control: no-store`, sin entidad JPA ni datos internos:

```json
{"tamanoTexto":"MEDIANO","altoContraste":false,"lecturaVoz":true}
```

`tamanoTexto`: PEQUENO / MEDIANO / GRANDE. PATCH admite uno o varios de esos
tres campos. Rechaza cuerpo vacío, campos desconocidos, null, tipos incorrectos
y enums ajenos mediante 400 SOLICITUD_INVALIDA. No admite recordatorios, propietario,
estado ni identidad como campos editables. No acepta autoridad del cliente.

El cuidador se obtiene del principal Bearer existente. Perfil ajeno, INACTIVO o
inexistente: mismo 404 PERFIL_NO_ENCONTRADO. Credencial inválida/rol incorrecto:
401/403 mediante la infraestructura existente; E3 comprueba dispositivo y perfil.
Fallos de persistencia: convención ProblemDetail existente, sin detalle interno.

## Persistencia y concurrencia

Defaults de entidad y SQL existentes: MEDIANO, contraste false, voz true;
recordatorioMinutos=10. GET sobre un perfil histórico sin fila devuelve los tres
defaults efectivos **sin insertar**. La primera escritura crea la configuración.

PATCH y reset son transacciones READ_COMMITTED. Bloquean/refrescan la fila del
perfil con PESSIMISTIC_WRITE, vuelven a comprobar pertenencia/estado, y refrescan
la configuración tras adquirir el bloqueo. Así serializan también la creación
de una fila faltante. PATCH modifica exclusivamente los campos enviados;
actualizaciones concurrentes a campos distintos se conservan. El mismo campo
queda con el último valor confirmado. No se usa fecha_actualizacion como versión:
su precisión de segundos no permite esa garantía.

Reset cambia únicamente tamaño/contraste/voz. Conserva recordatorio_minutos y
cualquier dato de usuario. Un fallo revierte la operación completa. El GET de
Usuario es solo lectura; no crea/modifica ejecución, progreso o guía inicial.

## Validación

`AccesibilidadServiceTest`: ownership, defaults históricos, validación, PATCH
parcial, reset, conservación de recordatorio, bloqueo y commit/rollback.
`AccesibilidadControllerTest` y `AccesibilidadUsuarioControllerTest`: rutas reales
con filtros de seguridad, DTO/no-store, identidad del principal, rol y revocación.
`AccesibilidadMySqlTest`: carrera de dos PATCH a campos distintos con fila
histórica ausente. Opt-in `DIAFACIL_TEST_MYSQL_*`, solo BD `diafacil_test_*` con
esquema preparado, sin DDL. Crea y elimina exclusivamente sus fixtures; nunca
ejecutarlo sobre la BD local de trabajo. No se ha ejecutado sin ese entorno.

La experiencia móvil, escalas, voz y matriz física se describen en
[accesibilidad frontend](../../frontend_dia_facil/docs/accesibilidad-perfil.md).
