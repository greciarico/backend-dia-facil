# Pictogramas ARASAAC — piloto de pasos

IconoActividad sigue representando actividades con sus 52 PNG propios. Solo
PlantillaPaso y PasoActividad usan Pictograma. El incremento C incorpora el
[POST de actividades](actividades-http.md) que resuelve selecciones por idArasaac.

## HTTP

GET `/api/pictogramas/arasaac?texto=lavar%20manos`, Bearer de cuidador, 200:

```json
{"pictogramas":[{"fuente":"ARASAAC","idArasaac":123,"nombre":"Etiqueta de prueba","urlImagen":"https://static.arasaac.org/pictograms/123/123_300.png"}]}
```

123 es sintético, NO un predeterminado elegido. La búsqueda no escribe en BD ni
acepta idCuidador como autoridad. Texto ausente/corto (<3 caracteres normalizados)
devuelve [] sin contactar ARASAAC. Más de 120 caracteres: 400 SOLICITUD_INVALIDA.
Normaliza espacios/mayúsculas. Usa únicamente el host oficial y español:
`GET https://api.arasaac.org/v1/pictograms/es/search/{texto}`, con texto codificado,
sin reenviar Bearer, cookies, email, perfil ni credenciales DiaFacil.

La API oficial no documenta paginación de búsqueda. Se devuelve metadata mínima
deduplicada por ID, conservando el orden recibido. Frontend muestra primero 3;
Ver más revela 12 y después bloques de 12 de la misma consulta. No se importa el
catálogo completo, no se inventa relevancia ni se rellenan coincidencias.

Timeout conexión 3 s / lectura 5 s. Límite técnico de respuesta 4 MiB. Cache de
100 consultas y 5 minutos, solo en memoria; incluye vacíos, nunca fallos. HTTP 404
de búsqueda se trata como ausencia; otros errores, timeout o JSON inválido producen
503 PICTOGRAMAS_NO_DISPONIBLES, distinto de []. No se conservan causas externas
que puedan contener URI/texto. No se añaden logs de consultas o credenciales.
No habilitar logging HTTP de cuerpos/URLs. El texto del paso se envía a ARASAAC;
las imágenes se solicitan desde el teléfono al CDN oficial.

## Modelo y datos base

idPictograma es la PK local. idArasaac es la identidad externa, nullable y UNIQUE
uq_pictograma_arasaac. Nombre deja de ser UNIQUE: varias imágenes pueden compartir
etiqueta. Fuente ARASAAC identifica el origen. Las filas ARASAAC se crean con
urlImagen=null: la URL oficial en color de 300px se calcula para los DTO.
url_imagen sigue nullable solo para preservar localizadores antiguos.

El DTO de plantilla añade fuente, idArasaac y URL calculada. Para recursos legacy
son null; no expone la ruta relativa como imagen renderizable. No consulta ARASAAC
al cargar plantillas o iniciar el backend. Las FKs de pasos no cambian.

El JSON admite nombre, fuente, idArasaac y palabras, sin urlImagen para ARASAAC.
Las referencias de pasos son `arasaac:ID`, no nombre ni PK autoincremental.
Durante compatibilidad otros legacy siguen aceptándose y buscándose por nombre
solo entre recursos sin ID externo. Una coincidencia legacy ambigua aborta y
revierte la transacción. ARASAAC se sincroniza por ID, conserva/advierte etiquetas
distintas y no sobrescribe datos. Nunca guarda URLs derivadas.

## Transición existente — no ejecutada por esta implementación

1. Detener backend, respaldar y ejecutar docs/sql/audit-pictogramas-arasaac.sql.
2. Revisar registros, localizadores, referencias reales y estructura.
3. Ejecutar docs/sql/prepare-pictogramas-arasaac.sql tras la revisión. Añade ID si
   falta, verifica duplicados/identidad e índice UNIQUE, permite URL nula y retira
   únicamente el UNIQUE legacy nominal esperado. No hace DML ni corrige conflictos.
   DDL MySQL confirma implícitamente: ante fallo inspeccionar antes de repetir.
4. Verificar SHOW CREATE TABLE. El inicializador exige el nuevo UNIQUE. Hibernate
   update no garantiza retirar el antiguo; usar la transición explícita.
5. No retirar url_imagen aún: requiere auditar URLs independientes y otra transición.

Para inspeccionar sin DDL ni carga usar ambas opciones:
`--spring.jpa.hibernate.ddl-auto=none --diafacil.datos-base.enabled=false`.
BD nueva: JPA y SQL v7 crean directamente las estructuras compatibles.

## Predeterminados de Desayunar aprobados

La usuaria verificó visualmente 2443, 5549, 36965, 2349 y 8688 y aprobó Fase 2.
El JSON utiliza esos cinco IDs sin URLs persistidas. Textos y orden de pasos no
cambian. Ver [mapeo y backfill de datos existente](desayunar-arasaac.md).
El inicializador no consulta ARASAAC ni sustituye referencias legacy pendientes;
advierte la transición necesaria sin crear reemplazos duplicados. La búsqueda
dinámica real fue validada por la usuaria en Android; las pruebas del adaptador
en este entorno siguen utilizando respuestas simuladas.

## Licencia e imágenes

Piloto no comercial confirmado. Los créditos se retiraron de Nueva actividad
por decisión UX; **no hay un apartado definitivo visible implementado**. Su
ubicación y cumplimiento deben revisarse antes de distribuir, incluyendo Sergio
Palao, ARASAAC, Gobierno de Aragón y CC BY-NC-SA según condiciones aplicables.
No se asume una versión de licencia no verificada ni se aplica al código completo.
Esta consolidación no realiza una revisión legal ni resuelve la atribución pendiente.
No hay Cloudinary ni copias propias del catálogo.

Expo Image usa contain y caché de disco al visualizar imágenes, sin descarga masiva.
La caché puede ser eliminada por el sistema: no garantiza offline permanente.
Ante fallo se conserva texto/elección y aparece una alternativa neutra.

Fuentes oficiales:
- https://api.arasaac.org/arasaac_v1.json
- https://arasaac.org/terms-of-use
- https://docs.expo.dev/versions/v57.0.0/sdk/image/

## Validación de este incremento

Fase 2: 182 pruebas backend correctas y 4 MySQL opt-in omitidas (sin BD desechable).
Compilación javac/JUnit con dependencias ya disponibles en target; el wrapper Maven
no pudo arrancar en este entorno. Logs locales: target/arasaac-compile.log y
target/arasaac-phase2-validation.log. El compilador devuelve 0 pero presenta el problema
preexistente de cierre de un JAR por permisos; la ejecución JUnit terminó sin fallos.
91 pruebas frontend correctas, TypeScript y lint. La exportación Android del
incremento anterior fue correcta (.expo/arasaac-export); no se repitió por estos
cambios de datos y retirada de la ayuda temporal. No se ejecutó SQL sobre la BD
local, no se validó la transición en MySQL real ni la UI en Android físico.
