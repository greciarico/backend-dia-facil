# Actividades — incremento C

## Crear una definición

`POST /api/perfiles/{idPerfilUsuario}/actividades`, Bearer de cuidador.
La identidad procede exclusivamente del principal `Identidad`; ningún campo
del cliente determina el cuidador. Perfil ajeno, inexistente o INACTIVO devuelve
el mismo 404 `PERFIL_NO_ENCONTRADO` antes de consultar ARASAAC o escribir.

```json
{
  "nombre": "Desayuno",
  "hora": "07:00",
  "codigoIcono": "bread",
  "pasos": [
    {"descripcion": "Lávate las manos", "idArasaac": 2443},
    {"descripcion": "Mi paso personalizado", "idArasaac": null}
  ]
}
```

Nombre obligatorio, trim exterior, máximo 80; hora local exacta HH:mm entre
00:00 y 23:59, sin fecha/zona/segundos; código de icono obligatorio y existente.
Mínimo un paso; descripción obligatoria/no blanca y máximo 120. No se transforma
el texto final de los pasos. Pictograma opcional, identificado únicamente por
idArasaac positivo. La posición del array genera orden 1..N; no se reciben claves
UI, orden redundante, plantilla, etiquetas o URL del proveedor.

**No hay un máximo funcional de pasos.** La capacidad preexistente de
`orden SMALLINT UNSIGNED` es exclusivamente una restricción de representación
del almacenamiento, no una política de accesibilidad ni de producto. El máximo
funcional se definirá después de la investigación correspondiente.

201 Created y Cache-Control: no-store:

```json
{"idActividad":27,"nombre":"Desayuno","hora":"07:00","codigoIcono":"bread"}
```

Sin Location hacia un detalle todavía inexistente, sin entidad JPA. Se permiten
nombres/horas coincidentes: no se añade una unicidad que el dominio no define.

## Resolución ARASAAC y atomicidad

Primero validar acceso/datos/icono y resolver metadata, sin transacción de
escritura abierta durante llamadas externas. IDs distintos se resuelven una vez:
reutilizar filas locales; para faltantes usar metadata oficial vigente de la
caché existente de búsqueda o GET oficial `/v1/pictograms/es/{id}`.
Nunca confiar en etiquetas o URL del teléfono, repetir búsqueda textual para
identificar una selección ni descargar imágenes. El detalle usa los mismos
timeouts/límites del cliente oficial y valida el ID devuelto. Un 404 confirmado
es pictograma inválido; fallos HTTP/timeout/metadata inválida son indisponibilidad.

La operación REQUIRES_NEW/READ_COMMITTED vuelve a comprobar perfil ACTIVO del
cuidador e icono, resuelve referencias locales y crea pictogramas faltantes,
Actividad y todos los PasoActividad en el mismo commit. Inicializa ACTIVA,
guiaInicialCompletada=false y pasos activo=true. Un fallo revierte todo.
Nuevos pictogramas: fuente ARASAAC, nombre oficial, urlImagen=null. No se cambian
filas existentes, cinco predeterminados, datos base, keywords ni plantillas.

El UNIQUE existente `uq_pictograma_arasaac` impide duplicados. Solo una colisión
identificada de esa clave reintenta tras rollback completo, en nueva transacción,
hasta tres intentos; relee la fila ganadora sin repetir consultas externas.
No se continúa con una transacción marcada para rollback ni se sobrescriben
etiquetas. Contención persistente: 409 CONFLICTO_CREACION. Otros fallos no se
confunden con esa colisión ni se reintentan automáticamente.

La definición y pasos son independientes de la plantilla. No se crean ejecuciones,
progreso, recordatorios, notificaciones ni historial. Sin cambios de esquema o SQL.

## Listado

`GET /api/perfiles/{idPerfilUsuario}/actividades` mantiene ownership, filtro ACTIVA,
404 de perfil no accesible, no-store y orden estable por ID. Cada resumen añade
hora HH:mm y codigoIcono (nullable para actividades antiguas), sin pasos/progreso.

```json
{"actividades":[{"idActividad":27,"nombre":"Desayuno","hora":"07:00","codigoIcono":"bread"}]}
```

## Errores y reintentos

ProblemDetail existente, sin detalles internos ni nuevos identificadores de seguimiento:

| HTTP | Código | Significado |
|---|---|---|
| 400 | SOLICITUD_INVALIDA | Campos inválidos; errores con campo/mensaje seguro |
| 400 | ICONO_NO_ENCONTRADO | Código de catálogo no disponible |
| 400 | PICTOGRAMA_INVALIDO | Identidad externa no válida |
| 404 | PERFIL_NO_ENCONTRADO | Mismo resultado para ajeno, inactivo e inexistente |
| 409 | CONFLICTO_CREACION | Contención de referencia externa tras rollback |
| 503 | PICTOGRAMAS_NO_DISPONIBLES | Falló la validación externa antes de escribir |
| 503/500 | SERVICIO_NO_DISPONIBLE / ERROR_INTERNO | Infraestructura/fallo inesperado |

No hay idempotencia persistente ni garantía de exactamente una creación tras
respuesta perdida. Frontend bloquea doble submit y nunca reenvía por red/timeout/
5xx o respuesta exitosa ilegible. Solo permite replay por el 401 renovable que
rechazó el comando antes de ejecución. Resultado desconocido conserva borrador,
invita a revisar Actividades y exige confirmación antes de otro intento manual.
Cancelar/abortar HTTP no prueba que backend haya revertido.

## Validación

Pruebas de contrato/seguridad, validaciones, metadatos oficiales simulados,
deduplicación, transacción/rollback y regresiones. La prueba MySQL opt-in
`CreacionActividadMySqlTest` exige DIAFACIL_TEST_MYSQL_URL en un esquema dedicado
diafacil_test_*, existente, sin DDL ni configuración local; prueba rollback real y
dos creaciones concurrentes con una sola referencia externa. Sin ese entorno se
omite explícitamente y no se ejecuta SQL en la base de desarrollo.

Resultado en este entorno: 216 pruebas backend correctas y cinco MySQL opt-in
omitidas. El wrapper Maven no puede arrancar; se compilaron fuentes y tests con
javac/Java 21 y se ejecutaron mediante JUnit Platform con dependencias locales.
El compilador devuelve 0, aunque mantiene el diagnóstico preexistente de permisos
al cerrar un JAR. Logs: target/increment-c-compile.log e increment-c-tests.log.
El smoke test general BackendApplicationTests.contextLoads requiere un datasource
de pruebas no configurado: falló al intentar arrancar y quedó fuera de la ejecución
aislada final; no se modificó para ocultar ese requisito. Sin prueba en MySQL real
ni Android físico en este entorno.
