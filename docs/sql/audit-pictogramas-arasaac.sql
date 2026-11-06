-- Solo lectura. Ejecutar antes de la transición y revisar resultados.
SHOW CREATE TABLE pictograma;
SELECT id_pictograma, nombre, url_imagen, fuente FROM pictograma ORDER BY id_pictograma;
SELECT nombre, COUNT(*) AS cantidad FROM pictograma GROUP BY nombre HAVING COUNT(*) > 1;
SELECT p.id_pictograma, p.nombre,
  (SELECT COUNT(*) FROM plantilla_paso t WHERE t.id_pictograma=p.id_pictograma) AS referencias_plantilla,
  (SELECT COUNT(*) FROM paso_actividad a WHERE a.id_pictograma=p.id_pictograma) AS referencias_actividad
FROM pictograma p ORDER BY p.id_pictograma;
-- Si id_arasaac ya existe, ejecutar también:
-- SELECT id_arasaac, COUNT(*) FROM pictograma WHERE id_arasaac IS NOT NULL
-- GROUP BY id_arasaac HAVING COUNT(*) > 1;
-- SELECT * FROM pictograma WHERE id_arasaac IS NOT NULL
-- AND (id_arasaac <= 0 OR fuente IS NULL OR fuente <> 'ARASAAC');
