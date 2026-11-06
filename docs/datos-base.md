# Inicialización funcional de datos base

Actualización ARASAAC: [modelo y transición](pictogramas-arasaac.md). Pictogramas
externos usan idArasaac UNIQUE y referencias arasaac:ID en el JSON, sin URL
persistida. El piloto Desayunar ya utiliza los cinco IDs aprobados; su
[backfill específico](desayunar-arasaac.md) es histórico y no se repite por esta ampliación.
La exigencia anterior de uq_pictograma_nombre se sustituye por uq_pictograma_arasaac;
en BD existentes preparar la transición estructural explícita antes de arrancar.

## Arranque y configuración

`diafacil.datos-base.enabled=true` es el valor compartido por defecto, en local y
en los demás entornos. Para deshabilitarlo usar configuración externa o
`--diafacil.datos-base.enabled=false`. No depende de diafacil.auth.enabled.

La configuración registra un ApplicationRunner que delega en el inicializador
después de crear JPA y antes de completar readiness. No ejecuta nada durante la
construcción del repositorio ni utiliza ApplicationReadyEvent. Un fallo de
esquema/BD/recurso impide completar el arranque; las diferencias de contenido
solo generan advertencias. No se cambia el mecanismo HTTP de autenticación.

Hibernate mantiene su responsabilidad de estructura: update en local, none en la
configuración general. Flyway y spring.sql.init siguen desactivados. En entornos
con ddl-auto=none se debe proporcionar el esquema correcto antes del arranque.
El inicializador nunca crea tablas, índices o restricciones.

## Fuente única ejecutable

`src/main/resources/db/datos-base.json` se incluye como recurso normal en el JAR.
El cargador usa ClassPathResource y el ObjectMapper de Spring; no necesita los
directorios frontend/documentacion en runtime. No interpreta SQL ni busca archivos
de imágenes en disco. No se añade generador SQL ni comando --check.

El catálogo ejecutable actual contiene:

- 52 iconos y 78 palabras clave de iconos;
- 18 pictogramas y 13 palabras clave de pictogramas;
- seis plantillas, 23 palabras clave de plantillas y 24 pasos ordenados;
- Desayunar intacta, con bread, sus tres palabras clave y los cinco pictogramas aprobados.

Una BD nueva recibe 214 filas base. Sus IDs son generados por BD, no constantes.
Las referencias del JSON usan código de icono, `arasaac:ID` para pictogramas
externos y nombre estable de plantilla; nombre de pictograma solo en compatibilidad legacy.
Cambiar un nombre usado como identidad no es una operación automática de renombrado.

## Catálogo de seis rutinas con curación aprobada

| Plantilla | Icono existente | Pasos | Pictogramas predeterminados |
| --- | --- | ---: | --- |
| Desayunar | bread | 5 | Los cinco aprobados, sin cambios |
| Ir al colegio | school | 4 | 8166, 17026, 37518, 37036 |
| Estudiar | open-book | 4 | 6640, 2359, 32814, 6474 |
| Almorzar | dish-of-food | 4 | 2443, 38944, 2349, 17298 |
| Cenar | food | 4 | 2443, 38944, 2349, 17298 |
| Prepararse para dormir | bed | 3 | 2522, 2326, 2369 |

IDs por orden. La curación se recuperó de las actividades persistidas 5–9 de la
BD de desarrollo y fue aprobada visualmente por el cuidador. La última actividad
real se llama Dormir; el nombre canónico de la plantilla permanece Prepararse para
dormir. Los 24 pasos base tienen predeterminado. Los 13 pictogramas incorporados
no reciben keywords inventadas; se mantienen las 13 asociaciones anteriores.
No se usan posiciones de búsqueda, URLs persistidas ni consultas ARASAAC al cargar.
La opcionalidad de pictograma en pasos propios sigue siendo válida.

Las seis familias de sugerencias, sus variantes y la corrección general de
prefijos están documentadas en [iconos HTTP](iconos-actividad-http.md). No cambia
el matching de plantillas ni su selección independiente de iconos. Cenar conserva
food como predeterminado, pero sus sugerencias usan bread, coffee y juice.
No se asignaron comer, comida, clases o tarea aislada a las nuevas plantillas.

### Transición de las plantillas ya existentes

El inicializador sigue siendo aditivo: no reemplaza pictogramas null de pasos
existentes ni elimina las antiguas asociaciones food–cena/cenar/cenando. Para la
BD donde se realizó la curación, preparar respaldo, detener todas las instancias
del backend y ejecutar explícitamente [esta transición](sql/promover-catalogo-seis-rutinas.sql)
en el cliente MySQL sobre la BD revisada, antes de arrancar con el JSON nuevo.
No ejecutar el SQL v7 completo. No usar una opción de continuar ante errores.

La transición requiere las cinco plantillas con textos/órdenes/iconos aprobados
y los 15 IDs ARASAAC distintos ya presentes por la curación. No inserta recursos
ni obtiene datos desde actividades de usuario. Verifica estructura InnoDB y UNIQUE
id_arasaac; bloquea filas y valida el grupo completo antes de escribir.
Solo completa 15 referencias null de PlantillaPaso y retira las tres keywords
autorizadas de food. Acepta referencias ya finales para permitir repetición.
Cualquier otro valor previo, paso extra/faltante o recurso no disponible aborta
con rollback. No cambia Desayunar, textos, órdenes, pictogramas ni pasos reales.
No depende de IDs internos fijos: resuelve nombre de plantilla, orden, código
de icono e idArasaac. La rutina auxiliar se crea y elimina solo al ejecutar el
script; nunca se registra en el arranque. Si falla, se conserva para inspección
y el siguiente intento recrea únicamente esa rutina auxiliar.

Resultado esperado en la BD anterior compatible: referencias_actualizadas=15,
keywords_retiradas=3; una repetición produce 0/0. Después, arrancar normalmente:
el inicializador agrega las keywords faltantes y un segundo arranque no inserta
filas. En otra BD existente que no tenga los pictogramas curados, el script aborta;
no improvisar sustitutos ni desactivar la protección del inicializador.
Una BD nueva se inicializa directamente desde el JSON y no necesita este script.
Los scripts históricos no se convierten en seeders; SQL v7 conserva su instantánea inicial.

Seleccionar otro pictograma en una actividad propia nunca modifica el catálogo.
Las futuras promociones también requerirán revisión explícita de datos base.

Validación (1 de noviembre de 2026): 248 pruebas frontend, TypeScript y lint pasan.
Transición probada realmente con el cliente MySQL en una BD desechable separada:
mapa completo, rechazo de texto/FK/fuente incompatibles, rollback después de UPDATE
ante fallo provocado en DELETE, repetición 0/0, 18 pictogramas sin duplicados y
Desayunar/actividades de prueba intactas. La BD desechable se eliminó al terminar;
no se ejecutó la transición en la BD de desarrollo. Se ampliaron las pruebas JUnit
de catálogo, matching, creación, idempotencia y MySQL opt-in. Maven sigue bloqueado
por acceso Java/Path.toRealPath a clases/JAR: pendientes esa suite en el entorno
normal y validación física de las nuevas sugerencias después de la transición.

## Ampliación del catálogo

Para ampliar los datos, editar el JSON; si se añade un icono, agregar su PNG y
require literal al catálogo frontend. No es necesario editar clases Java, ejecutar
INSERT ni sincronizar otro catálogo backend. El SQL v7 conserva la estructura y
una instantánea de datos de referencia; el seed SQL manual es histórico/excepcional.
Los tests frontend consultan el JSON para el catálogo vigente y mantienen pruebas
separadas de integridad de esas referencias históricas.

Los scripts audit/backfill/cleanup de la separación anterior permanecen históricos.
No se invocan por el runner. El inicializador no descarga imágenes ARASAAC ni
realiza búsquedas. Selección/copia de plantillas y resolución de pictogramas al
crear actividades pertenecen a sus [servicios actuales](actividades-http.md).
ARASAAC se identifica por ID y su URL se deriva en DTO; las rutas legacy no se
convierten automáticamente ni sustituyen selecciones existentes.

## Política de sincronización

El recurso completo se valida antes de abrir la transacción: campos, longitudes,
claves duplicadas, palabras repetidas, referencias y órdenes dentro del rango SQL.
Las referencias opcionales siguen siendo opcionales. También se valida al invocar
directamente el sincronizador, antes de su primera operación persistente.

| Caso | Resultado |
|---|---|
| Falta icono/pictograma/plantilla compatible | Insertar por su clave natural |
| Registro coincide | Conservar ID y atributos |
| Código de icono con otra etiqueta | Conservar, advertir y permitir relaciones por código |
| Pictograma con ID/fuente incompatible, o URL distinta si es legacy | Conservar y omitir keywords/nuevas dependencias incompatibles |
| Pictograma ARASAAC con etiqueta diferente y mismo ID/fuente | Conservar y advertir; permitir dependencias/keywords por identidad externa |
| Keyword faltante de recurso compatible | Insertar; conservar las adicionales |
| Plantilla con icono distinto, incluso null | Conservar y omitir pasos/keywords de ese grupo |
| Pasos existentes coinciden y faltan posiciones | Insertar solo los faltantes |
| Pasos distintos, adicionales o duplicados | Conservar el grupo sin completar huecos ni keywords |
| Definición retirada del JSON | No borrar filas existentes |

Se compara todo el grupo de pasos antes de completar faltantes. Los informes
contienen claves del seed y tipo de diferencia, no filas ni valores de usuarios.
Solo se registra el resumen después del commit.

El adaptador JPA tiene operaciones tipadas de lectura e inserción para las siete
entidades base. No expone update/delete ni accede a cuidadores, perfiles, actividades,
pasos reales, ejecuciones o sesiones. Las factories añadidas solo construyen filas
nuevas. No hay REPLACE, vaciado de tablas, reinicio de IDs o actualización en cascada
de copias personalizadas. Los huecos normales de AUTO_INCREMENT tras rollback no
equivalen a reiniciar o reasignar IDs.

## Transacciones e integridad

Cada intento usa REQUIRES_NEW y READ_COMMITTED. Las UNIQUE naturales de los catálogos,
keywords y plantilla/orden resuelven carreras; no hay bloqueo pesimista de la plantilla,
tabla de control ni locks distribuidos. Se reintenta hasta tres veces únicamente tras
rollback por colisiones de esas UNIQUE o deadlock/timeout de bloqueo MySQL. Otros
errores se propagan; un error persistente impide el arranque.

Antes de cualquier escritura se verifican mediante metadatos JDBC los nombres y
columnas de las siete UNIQUE requeridas. Incluye uq_plantilla_paso_orden sobre
(id_plantilla_actividad, orden). Un índice no único o una restricción con otra forma
no sirve. La ausencia es un error estructural, no un conflicto de contenido ignorable.

Hibernate update usa RECREATE_QUIETLY para no quitar UNIQUE existentes al reiniciar.
Esa estrategia todavía puede ignorar errores al añadirlas: la verificación runtime
es obligatoria. No se confía en mensajes de arranque de Hibernate para aceptarlas.

## BD existente: hacer ANTES del primer arranque nuevo con update

La comprobación debe preceder al arranque: Hibernate procesa el esquema antes del
runner. No se ha conectado ni alterado tu BD local desde este incremento.

1. Respaldar y detener escrituras de backend y herramientas SQL.
2. Seleccionar/verificar la base y ejecutar **sql/audit-plantilla-paso-orden.sql**.
   La consulta GROUP BY/HAVING detecta duplicados y otra consulta muestra sus IDs,
   descripción y pictograma. No cambia datos.
3. Si hay duplicados, **detenerse y revisarlos manualmente**. No hay script que
   elimine, reordene, fusione o escoja un ganador. No arrancar la versión nueva con
   update mientras se decide su resolución.
4. Si no hay conflictos, ejecutar **sql/add-plantilla-paso-orden-unique.sql** con un
   cliente que se detenga al primer error (sin --force). Requiere CREATE ROUTINE/ALTER.
   Vuelve a comprobar duplicados y definiciones antes del primer ALTER. Añade la
   UNIQUE y retira idx_plantilla_paso_orden solo si era el índice esperado redundante.
5. Si existe una UNIQUE equivalente con otro nombre, el script se detiene para no
   duplicarla. Tras verificar exactamente columnas/orden, normalizar su nombre con
   `ALTER TABLE plantilla_paso RENAME INDEX nombre_revisado TO uq_plantilla_paso_orden;`
   y repetir el script. No renombrar un índice desconocido sin revisar.
   Si las columnas están en el orden inverso, detenerse y revisar una transición
   explícita de índice; renombrarlo no cambia el orden y el script no añadirá otra UNIQUE.
6. Verificar SHOW CREATE TABLE y arrancar normalmente. Un segundo arranque debe
   conservar datos/IDs y comunicar cero filas creadas si todo estaba completo.

El script no ejecuta DML sobre registros; puede repetirse con el esquema correcto.
MySQL DDL confirma implícitamente: ante un fallo posterior, inspeccionar el estado
real, no asumir rollback global de los ALTER. Nunca desactivar FK para forzarlo.
Para arrancar únicamente con fines de inspección sin DDL ni carga usar juntos
`--spring.jpa.hibernate.ddl-auto=none --diafacil.datos-base.enabled=false`.
Deshabilitar solo datos-base NO impide el DDL de Hibernate.

Una BD nueva no necesita esa transición manual: Hibernate genera directamente la
UNIQUE sin el índice redundante, antes de que el inicializador cargue los datos.

## Pruebas y limitaciones verificadas

Tests unitarios de recurso, políticas, idempotencia, ampliación, validación previa,
rollback/reintentos y metadatos; tests de configuración habilitada por defecto y
deshabilitación sin dependencias JPA/JSON. Las consultas del adaptador se compilan
contra Hibernate/modelo real sin JDBC. El fixture SQL v7 y DDL reflejan la UNIQUE.

DatosBaseMySqlTest es opt-in y requiere una base VACÍA exclusiva
diafacil_test_datos_base_* con las variables DIAFACIL_TEST_DATOS_BASE_MYSQL_URL,
DIAFACIL_TEST_DATOS_BASE_MYSQL_USER y DIAFACIL_TEST_DATOS_BASE_MYSQL_PASSWORD.
No usa credenciales locales. Comprueba update desde cero, carga/repetición, rollback
real, dos arranques simultáneos, orden repetido en otra plantilla, preservación de
filas de usuario y transición que rechaza duplicados sin corregirlos. Crea y elimina
solo objetos de esa base desechable; no configurarla con diafacil ni una base compartida.

Validación disponible: 156 pruebas backend correctas; tres MySQL opt-in omitidas.
Javac/JUnit con dependencias de target, sin conexión a MySQL/SMTP. Logs en
target/base-compile.log y target/base-validation.log. El entorno conserva el fallo
de cierre de JAR de javac con salida 0; las clases se generan y los tests pasan.
No se afirma ejecución real de la transición ni de concurrencia MySQL.
Frontend: 68 pruebas correctas; TypeScript y ESLint. No se modifican pantallas,
assets, navegación, tokens ni autenticación. El empaquetado Maven completo requiere
resolver el acceso del entorno a su repositorio local; el recurso classpath sí se
comprueba en las pruebas.

## Archivos

Nuevos: db/datos-base.json; configuración DatosBaseConfiguration; modelo/lector,
store, sincronizador, verificador e inicializador en service/datosbase; adaptador
JpaDatosBaseStore; scripts de auditoría/UNIQUE; esta guía; tests de datos base,
configuración, transacciones, metadatos e integración opt-in.
Modificados: factories de las siete entidades base, UNIQUE de PlantillaPaso,
application.properties, fixture/test de persistencia, SQL v7, documentación funcional
y README; test frontend de iconos para usar el JSON vigente. Se preservan los cambios
anteriores del workspace. No se eliminan archivos ni se aplica la transición local.
