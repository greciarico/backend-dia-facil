-- READ ONLY. Run BEFORE starting the new version with ddl-auto=update.
SELECT DATABASE() AS base_revisada;
SELECT id_plantilla_actividad, orden, COUNT(*) AS cantidad
FROM plantilla_paso GROUP BY id_plantilla_actividad, orden HAVING COUNT(*) > 1;

SELECT p.id_plantilla_paso, p.id_plantilla_actividad, t.nombre AS plantilla,
       p.orden, p.descripcion, p.id_pictograma
FROM plantilla_paso p JOIN plantilla_actividad t USING (id_plantilla_actividad)
JOIN (SELECT id_plantilla_actividad, orden FROM plantilla_paso
      GROUP BY id_plantilla_actividad, orden HAVING COUNT(*) > 1) d
  ON d.id_plantilla_actividad = p.id_plantilla_actividad AND d.orden = p.orden
ORDER BY p.id_plantilla_actividad, p.orden, p.id_plantilla_paso;

SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plantilla_paso'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
SHOW CREATE TABLE plantilla_paso;
