-- Transición estructural explícita, nunca ejecutada al arrancar.
-- Detener backend, respaldar y revisar audit-pictogramas-arasaac.sql antes.
-- No asigna los cinco IDs pendientes de aprobación ni modifica datos.
DELIMITER $$
DROP PROCEDURE IF EXISTS df_prepare_pictogramas_arasaac$$
CREATE PROCEDURE df_prepare_pictogramas_arasaac()
BEGIN
  DECLARE cantidad INT DEFAULT 0;
  SELECT COUNT(*) INTO cantidad FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pictograma' AND COLUMN_NAME='id_arasaac';
  IF cantidad = 0 THEN
    ALTER TABLE pictograma ADD COLUMN id_arasaac INT NULL;
  END IF;
  IF EXISTS (SELECT id_arasaac FROM pictograma WHERE id_arasaac IS NOT NULL
      GROUP BY id_arasaac HAVING COUNT(*) > 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IDs ARASAAC duplicados: revisar sin corregir automaticamente';
  END IF;
  IF EXISTS (SELECT 1 FROM pictograma WHERE id_arasaac IS NOT NULL
      AND (id_arasaac <= 0 OR fuente IS NULL OR fuente <> 'ARASAAC')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Identidad ARASAAC inconsistente: revisar registros';
  END IF;
  SELECT COUNT(*) INTO cantidad FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pictograma' AND INDEX_NAME='uq_pictograma_arasaac';
  IF cantidad > 0 AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pictograma' AND INDEX_NAME='uq_pictograma_arasaac'
      AND COLUMN_NAME='id_arasaac' AND NON_UNIQUE=0 AND SEQ_IN_INDEX=1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Indice ARASAAC inesperado: revisar estructura';
  END IF;
  IF cantidad > 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Indice ARASAAC compuesto inesperado';
  END IF;
  IF cantidad = 0 THEN
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='pictograma' AND COLUMN_NAME='id_arasaac' AND NON_UNIQUE=0) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unique ARASAAC con otro nombre: revisar antes de duplicar';
    END IF;
    ALTER TABLE pictograma ADD CONSTRAINT uq_pictograma_arasaac UNIQUE (id_arasaac);
  END IF;
  ALTER TABLE pictograma MODIFY COLUMN url_imagen VARCHAR(255) NULL;
  SELECT COUNT(*) INTO cantidad FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pictograma' AND INDEX_NAME='uq_pictograma_nombre';
  IF cantidad > 0 THEN
    IF cantidad <> 1 OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pictograma' AND INDEX_NAME='uq_pictograma_nombre'
        AND COLUMN_NAME='nombre' AND NON_UNIQUE=0) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Indice legacy inesperado: revisar antes de retirar';
    END IF;
    ALTER TABLE pictograma DROP INDEX uq_pictograma_nombre;
  END IF;
END$$
CALL df_prepare_pictogramas_arasaac()$$
DROP PROCEDURE df_prepare_pictogramas_arasaac$$
DELIMITER ;
