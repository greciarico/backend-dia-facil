-- MANUAL ONLY, AFTER audit-plantilla-paso-orden.sql and backup, BEFORE restarting.
-- Stop ALL writes; choose the reviewed database; stop on the first error (no --force).
-- No DELETE, UPDATE, renumbering or merging of existing rows. MySQL DDL commits implicitly.
DELIMITER $$
DROP PROCEDURE IF EXISTS df_unique_plantilla_paso_orden$$
CREATE PROCEDURE df_unique_plantilla_paso_orden()
BEGIN
  IF DATABASE() IS NULL OR NOT EXISTS (SELECT 1 FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plantilla_paso') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Seleccione la base existente y audite plantilla_paso.';
  END IF;
  IF EXISTS (SELECT 1 FROM plantilla_paso GROUP BY id_plantilla_actividad, orden HAVING COUNT(*) > 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ordenes duplicados en plantilla_paso; revisar sin correccion automatica.';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'plantilla_paso' AND INDEX_NAME = 'idx_plantilla_paso_orden')
    AND NOT EXISTS (SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'plantilla_paso' AND INDEX_NAME = 'idx_plantilla_paso_orden'
      GROUP BY INDEX_NAME HAVING MIN(NON_UNIQUE) = 1 AND COUNT(*) = 2
      AND GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'id_plantilla_actividad,orden') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Indice antiguo con definicion inesperada; revisar antes de alterar.';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'plantilla_paso' AND INDEX_NAME = 'uq_plantilla_paso_orden') THEN
    IF NOT EXISTS (SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'plantilla_paso' AND INDEX_NAME = 'uq_plantilla_paso_orden'
        GROUP BY INDEX_NAME HAVING MAX(NON_UNIQUE) = 0 AND COUNT(*) = 2
        AND GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'id_plantilla_actividad,orden') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UNIQUE con nombre esperado pero definicion incorrecta; revisar.';
    END IF;
  ELSE
    IF EXISTS (SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'plantilla_paso' GROUP BY INDEX_NAME HAVING MAX(NON_UNIQUE) = 0 AND COUNT(*) = 2
        AND SUM(COLUMN_NAME = 'id_plantilla_actividad') = 1 AND SUM(COLUMN_NAME = 'orden') = 1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ya existe UNIQUE equivalente; revisar nombre/orden, no duplicar.';
    END IF;
    ALTER TABLE plantilla_paso ADD CONSTRAINT uq_plantilla_paso_orden UNIQUE (id_plantilla_actividad, orden);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'plantilla_paso' AND INDEX_NAME = 'idx_plantilla_paso_orden') THEN
    ALTER TABLE plantilla_paso DROP INDEX idx_plantilla_paso_orden;
  END IF;
END$$
CALL df_unique_plantilla_paso_orden()$$
DROP PROCEDURE df_unique_plantilla_paso_orden$$
DELIMITER ;
SHOW CREATE TABLE plantilla_paso;
