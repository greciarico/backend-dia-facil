-- READ ONLY. Select the reviewed database first.
-- Run before cleanup, while actividad/plantilla_actividad.id_pictograma still exist.
SELECT DATABASE() AS base_revisada;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
  ('icono_actividad', 'palabra_clave_icono_actividad', 'actividad', 'plantilla_actividad',
   'pictograma', 'palabra_clave_pictograma', 'paso_actividad', 'plantilla_paso')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
  ('icono_actividad', 'palabra_clave_icono_actividad', 'palabra_clave_pictograma', 'actividad', 'plantilla_actividad', 'paso_actividad', 'plantilla_paso')
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, CONSTRAINT_NAME, DELETE_RULE, UPDATE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME IN
  ('palabra_clave_icono_actividad', 'palabra_clave_pictograma', 'actividad', 'plantilla_actividad', 'paso_actividad', 'plantilla_paso');

-- Also expose incoming relations from tables outside the expected model.
SELECT TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'pictograma';

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
  ('icono_actividad', 'palabra_clave_icono_actividad', 'actividad', 'plantilla_actividad')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- Every old resource, including unexpected ones. No IDs are assumed.
SELECT p.id_pictograma, p.nombre, p.url_imagen, p.fuente,
  (SELECT COUNT(*) FROM actividad a WHERE a.id_pictograma = p.id_pictograma) AS actividades,
  (SELECT COUNT(*) FROM plantilla_actividad t WHERE t.id_pictograma = p.id_pictograma) AS plantillas,
  (SELECT COUNT(*) FROM paso_actividad s WHERE s.id_pictograma = p.id_pictograma) AS pasos_reales,
  (SELECT COUNT(*) FROM plantilla_paso s WHERE s.id_pictograma = p.id_pictograma) AS pasos_plantilla,
  (SELECT COUNT(*) FROM palabra_clave_pictograma k WHERE k.id_pictograma = p.id_pictograma) AS palabras
FROM pictograma p ORDER BY p.id_pictograma;

SELECT id_actividad, id_pictograma FROM actividad WHERE id_pictograma IS NOT NULL;
SELECT id_plantilla_actividad, nombre, id_pictograma FROM plantilla_actividad WHERE id_pictograma IS NOT NULL;
-- Check SHOW CREATE TABLE individually for any unexpected constraint/index definitions.
