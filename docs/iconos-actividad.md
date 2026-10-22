# Iconos propios de actividades y pictogramas de pasos

Actualización posterior: [inicialización de datos base](datos-base.md) utiliza
datos-base.json automáticamente. La secuencia de esta guía es histórica para la
separación de catálogos; audit/backfill/cleanup no se ejecutan al arrancar. El seed
SQL manual queda como referencia, no como fuente vigente del inicializador.

## Modelo y alcance

IconoActividad tiene id_icono_actividad INT autoincremental, codigo VARCHAR(60)
único y nombre VARCHAR(80), ambos obligatorios. Código estable en inglés, etiqueta
descriptiva en español; no URL, ruta física, imagen binaria, estado ni proveedor.
El catálogo inicial contiene los 52 códigos de los PNG existentes en frontend.
Un cambio de etiqueta no cambia el código ni las asociaciones.

PalabraClaveIconoActividad tiene ID INT autoincremental, icono obligatorio y
palabra VARCHAR(60). UNIQUE(icono,palabra) permite una palabra en varios iconos.
Índice por palabra; el UNIQUE ya cubre búsquedas por icono. No se duplica ese índice.

Actividad y PlantillaActividad apuntan opcionalmente a IconoActividad mediante
id_icono_actividad (ManyToOne LAZY, fk_actividad_icono/fk_plantilla_icono, SET NULL).
No hay UNIQUE en esas FK. Pasos reales y de plantilla siguen apuntando a Pictograma.
PalabraClaveActividad sigue apuntando a PlantillaActividad; no es la búsqueda de
iconos. PalabraClavePictograma continúa para acciones de los pasos.

La referencia final es ../documentacion-dia-facil/scrip_diafacil_v7.sql desde la
raíz del backend. Conserva Desayunar, sus tres palabras de plantilla y sus cinco
pasos ordenados; su icono ahora es bread. Los cinco pictogramas de acciones y sus
palabras permanecen. ARASAAC, sugerencias, selector, formulario y POST de actividad
no se implementan. Los contratos GET y la autenticación no cambian.

## No confundir esquema final con transición local

En una base nueva, JPA/SQL v7 solo tienen las relaciones nuevas de actividad y
plantilla. En una base existente, update puede agregar estructuras, pero deja
id_pictograma y sus FK antiguas físicamente presentes. Java ya no las utiliza.
No copiar SQL v7 como schema.sql/data.sql ni ejecutarlo entero sobre datos existentes.
Spring SQL init y Flyway siguen deshabilitados; el nuevo cargador usa únicamente
el recurso JSON empaquetado, sin ejecutar los scripts históricos siguientes.

No se ha conectado ni modificado la BD local durante este incremento. Los
resultados de Hibernate sin JDBC comprueban el modelo generado, no garantizan que
tu esquema histórico haya convergido. La auditoría real siguiente es obligatoria.

## Secuencia manual revisable

Usar una sesión MySQL con la base revisada seleccionada y conexión UTF-8 (por
ejemplo --default-character-set=utf8mb4) para los acentos de etiquetas y palabras.
Respaldar esquema y datos antes de empezar; detener escrituras de la aplicación y de otras herramientas hasta
terminar la transición. No usar --force ni un cliente que continúe tras errores.
DDL MySQL hace commits implícitos: el respaldo, no un ROLLBACK global, es la vía de
recuperación tras cambios de esquema. No ejecutar scripts de este directorio sobre
una base elegida por defecto sin comprobar SELECT DATABASE().

1. Ejecutar **sql/audit-iconos-actividad.sql** antes de eliminar columnas. Es solo
   lectura: inventario de columnas, restricciones, índices y todas las referencias
   antiguas, incluidos usos de los cinco recursos como pasos. Guardar el resultado.
2. Arrancar la versión nueva en local para que Hibernate haga la expansión, y volver
   a detener el backend. No asumir que update migra o elimina asociaciones antiguas.
3. Repetir la auditoría y revisar SHOW CREATE TABLE de icono_actividad,
   palabra_clave_icono_actividad, actividad y plantilla_actividad. Deben existir:
   - ambas tablas nuevas, InnoDB y utf8mb4/utf8mb4_unicode_ci;
   - codigo VARCHAR(60) NOT NULL con uq_icono_actividad_codigo y nombre VARCHAR(80);
   - palabras obligatorias, UNIQUE(icono,palabra), índice palabra y FK CASCADE;
   - id_icono_actividad INT NULL en actividad y plantilla, con FK SET NULL;
   - las dos columnas/FK antiguas aún presentes y ambas relaciones de pasos intactas.
   Si falta algo o hay restricciones distintas, corregirlo mediante ALTER explícito
   revisado para ese esquema y repetir la comprobación. No improvisar borrados.
4. Ejecutar **sql/seed-iconos-actividad.sql**. Solo inserta códigos/palabras ausentes,
   sin REPLACE, IDs fijos ni sobrescritura de etiquetas ya existentes. Repetible con
   escrituras pausadas. No convierte datos antiguos. Verificar los 52 códigos; las
   palabras iniciales cubren los cinco recursos migrados, no todos los PNG.
5. Ejecutar **sql/backfill-iconos-actividad.sql**. Requiere CREATE ROUTINE. Usa una
   tabla temporal de mapeo y una transacción DML con rollback/resignal ante error.
   Copia también palabras antiguas adicionales, conserva todo el origen y completa
   solo asociaciones nuevas null. Una repetición antes de la limpieza no duplica
   palabras ni sobrescribe destinos. Comprobar asociaciones antes/después y los pasos.
6. Revisar los resultados y autorizar por separado la limpieza. El script
   **sql/cleanup-iconos-actividad.sql** prepara una rutina, pero tiene CALL comentado.
   Solo después de la autorización ejecutar la llamada y retirar la rutina al terminar.
   Si se detiene, inspeccionar la causa antes de repetir o modificar nada.

Mapeo exclusivo de los antiguos seeds, con nombre y URL binariamente iguales y
fuente NULL (cualquier variación requiere revisión):

| Nombre anterior | URL anterior | Código nuevo |
|---|---|---|
| Pan | /pictogramas/pan.png | bread |
| Libros | /pictogramas/libros.png | open-book |
| Almuerzo | /pictogramas/almuerzo.png | dish-of-food |
| Caminar | /pictogramas/caminar.png | walk |
| Dormir | /pictogramas/dormir.png | bed |

No hay books.png. No se busca por nombre de actividad ni se reasignan IDs de
pictogramas a iconos por coincidencia numérica.

El traslado se detiene si encuentra referencias fuera de ese mapeo, códigos de
destino ausentes o destinos nuevos contradictorios. No se borra ni se convierte
automáticamente un caso desconocido a null. Revisar el mapeo con datos concretos.

## Qué elimina la limpieza, y cuándo se bloquea

Antes de cualquier ALTER verifica nombres y destinos de las FK antiguas, traslado
completo, copia de todas las palabras y ausencia de usos en ambos tipos de pasos.
Si un recurso antiguo se usa en un paso, no cambia nada permanente: conservarlo y
revisar su semántica antes de diseñar una limpieza adaptada. No anular su imagen.

Tras superar esas comprobaciones elimina exactamente:

- actividad.fk_actividad_pictograma y actividad.id_pictograma;
- plantilla_actividad.fk_plantilla_pictograma y plantilla_actividad.id_pictograma;
- las cinco filas antiguas identificadas, con CASCADE de sus palabras ya copiadas.

No elimina Pictograma, las cinco acciones de pasos, plantillas, pasos ni sus palabras.
Los ALTER se realizan antes del DELETE transaccional. Un fallo de DDL puede dejar
una fase parcial confirmada: el script la detecta al repetir y exige revisar el
esquema; no desactivar FK, forzar la repetición ni asumir rollback de los ALTER.
Con columnas antiguas ya retiradas no se vuelve a ejecutar el backfill.

Verificación final: SHOW CREATE TABLE de las cuatro tablas, ausencia de las dos
columnas/FK antiguas, Desayunar asociado a bread, cinco pasos con sus pictogramas,
palabras conservadas y reinicio sin recrear columnas antiguas. El usuario local
puede tener más recursos que los cinco seeds: no exigir COUNT(pictograma)=5 si
existen otros legítimos; verificar identidad y referencias, no solo cantidades.

## Frontend y pruebas

src/constants/activity-icons.ts tiene 52 require literales y el tipo ActivityIconCode.
resolveActivityIcon devuelve undefined para códigos desconocidos, rutas o propiedades
heredadas. No existe import de este catálogo desde una pantalla: se prepara para
el siguiente incremento sin añadir los PNG a una UI ni alterar navegación.

Pruebas sin BD: metadatos JPA y DDL MySQL generado a target, fixture SQL v7,
relaciones separadas, proyección del GET existente, catálogo/assets y seeds.
El test frontend test:activity-icons comprueba también conservación de Desayunar.

IconosActividadMySqlTest es opt-in. Requiere una base VACÍA exclusiva
diafacil_test_iconos_* y las variables DIAFACIL_TEST_ICONOS_MYSQL_URL,
DIAFACIL_TEST_ICONOS_MYSQL_USER y DIAFACIL_TEST_ICONOS_MYSQL_PASSWORD. No lee
application-local.properties. Necesita permisos de tablas y rutinas en esa base;
crea fixtures compactos, ejecuta los scripts reales y elimina solo sus objetos.
No configurar estas variables con diafacil ni con una base de pruebas compartida.
Comprueba repetición de seed/backfill, etiquetas conservadas, IDs no coincidentes,
conflictos, referencias desconocidas, palabras pendientes, protección de ambos
tipos de pasos y limpieza completa. No activa DDL en la aplicación.

Las pruebas transaccionales y de esquema con MySQL deben ejecutarse antes de
autorizar la limpieza local. Si la base de pruebas no está disponible, se informa
la omisión; un parser o simulación no sustituye ejecutar los scripts en MySQL.

## Validación del incremento

139 pruebas backend correctas con javac/JUnit y dependencias locales de target;
dos MySQL opt-in omitidas por falta de configuración. El compilador del entorno
mantiene una excepción al cerrar un JAR con salida 0; las clases se generan y las
pruebas pasan. Maven offline no pudo abrir C:\.m2\repository; el intento se conserva
en target/icon-maven.log. Logs aislados en target/icon-compile.log y target/icon-validation.log.
No se afirma una validación de expansión o de migración en MySQL local.

Frontend: 68 pruebas correctas (17 auth, 16 sesión, 30 perfiles/actividades y cinco
catálogo), TypeScript, ESLint y exportación Android. Sin nuevas dependencias.

Archivos nuevos: entidades IconoActividad y PalabraClaveIconoActividad;
IconosActividadMySqlTest; esta guía y los cuatro scripts SQL; catálogo frontend
constants/activity-icons.ts y scripts/test-activity-icons.cjs.
Modificados: entidades Actividad/PlantillaActividad; PersistenceModelTest,
PerfilUsuarioRepositoryTest y fixture sql-v7-model.tsv; SQL v7 y definición funcional;
README de ambos proyectos, docs/persistence.md y package.json (solo script de prueba).
No se eliminan archivos ni se modifican los 52 PNG proporcionados.
