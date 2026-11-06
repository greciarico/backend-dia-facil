-- Fase 2 aprobada: solo los cinco pictogramas base existentes (PK 2-6).
-- Detener backend/escrituras y respaldar ANTES de ejecutar y antes de arrancar
-- con el nuevo datos-base.json. La transición estructural ARASAAC debe estar lista.
-- Seleccionar explícitamente la BD revisada. No usar continuar-ante-errores.
-- No cambia plantilla_paso, pasos reales, keywords, PK/FK ni otros datos.
-- Mapeo histórico aprobado; la fuente ejecutable del inicializador sigue siendo JSON.
SET NAMES utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS df_backfill_desayunar_arasaac$$
CREATE PROCEDURE df_backfill_desayunar_arasaac()
BEGIN
  DECLARE plantilla INT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS df_desayunar_arasaac;
    RESIGNAL;
  END;
  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Selecciona y verifica la BD antes del backfill';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME IN ('pictograma','plantilla_actividad','plantilla_paso','paso_actividad')
      AND ENGINE='InnoDB') <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Se requiere estructura InnoDB revisada';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='pictograma' AND INDEX_NAME='uq_pictograma_arasaac') <> 1
      OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='pictograma' AND INDEX_NAME='uq_pictograma_arasaac'
        AND COLUMN_NAME='id_arasaac' AND NON_UNIQUE=0 AND SEQ_IN_INDEX=1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Falta UNIQUE id_arasaac esperado; revisar transicion estructural';
  END IF;
  START TRANSACTION;
  CREATE TEMPORARY TABLE df_desayunar_arasaac (
    id_local INT PRIMARY KEY, id_arasaac INT NOT NULL UNIQUE,
    nombre_legacy VARCHAR(80) NOT NULL, url_legacy VARCHAR(255) NOT NULL,
    etiqueta VARCHAR(80) NOT NULL, orden INT NOT NULL, descripcion VARCHAR(120) NOT NULL
  ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  INSERT INTO df_desayunar_arasaac VALUES
    (2,2443,'Lavarse las manos','/pictogramas/lavar-manos.png','enjabonar las manos',1,'Lávate las manos'),
    (3,5549,'Preparar la mesa','/pictogramas/mesa.png','poner la mesa',2,'Prepara la mesa'),
    (4,36965,'Servir la comida','/pictogramas/servir.png','servir',3,'Sirve la comida'),
    (5,2349,'Comer','/pictogramas/comer.png','comer',4,'Come'),
    (6,8688,'Limpiar y recoger','/pictogramas/limpiar.png','secar',5,'Limpia y recoge');

  -- Mantener bloqueadas las filas durante comprobación y actualización.
  SELECT p.id_pictograma FROM pictograma p JOIN df_desayunar_arasaac m
    ON m.id_local=p.id_pictograma ORDER BY p.id_pictograma FOR UPDATE;
  IF (SELECT COUNT(*) FROM pictograma p JOIN df_desayunar_arasaac m ON m.id_local=p.id_pictograma) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Faltan PK 2-6 esperadas; no se insertan ni reasignan filas';
  END IF;
  IF EXISTS (SELECT 1 FROM pictograma p JOIN df_desayunar_arasaac m ON m.id_local=p.id_pictograma
      WHERE NOT (((p.id_arasaac IS NULL AND p.fuente IS NULL
          AND BINARY p.nombre=BINARY m.nombre_legacy AND BINARY p.url_imagen=BINARY m.url_legacy) IS TRUE)
        OR ((p.id_arasaac=m.id_arasaac AND BINARY p.fuente=BINARY 'ARASAAC'
          AND BINARY p.nombre=BINARY m.etiqueta AND p.url_imagen IS NULL) IS TRUE))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicto en valores previos/finales de pictogramas; revisar sin sobrescribir';
  END IF;
  IF EXISTS (SELECT 1 FROM pictograma p JOIN df_desayunar_arasaac m ON m.id_arasaac=p.id_arasaac
      WHERE p.id_pictograma<>m.id_local) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ID ARASAAC ya pertenece a otra PK; revisar duplicados sin fusionar';
  END IF;
  IF (SELECT COUNT(*) FROM plantilla_actividad WHERE BINARY nombre=BINARY 'Desayunar') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='No existe una unica plantilla Desayunar esperada';
  END IF;
  SELECT id_plantilla_actividad INTO plantilla FROM plantilla_actividad
    WHERE BINARY nombre=BINARY 'Desayunar' FOR UPDATE;
  SELECT t.id_plantilla_paso FROM plantilla_paso t
    WHERE t.id_plantilla_actividad=plantilla ORDER BY t.orden FOR UPDATE;
  IF (SELECT COUNT(*) FROM plantilla_paso WHERE id_plantilla_actividad=plantilla) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Desayunar no conserva exactamente cinco pasos';
  END IF;
  IF (SELECT COUNT(*) FROM plantilla_paso t JOIN df_desayunar_arasaac m
      ON t.id_pictograma=m.id_local AND t.orden=m.orden AND BINARY t.descripcion=BINARY m.descripcion
      WHERE t.id_plantilla_actividad=plantilla) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Texto/orden/FK de Desayunar difiere del aprobado; no se corrige';
  END IF;
  IF EXISTS (SELECT 1 FROM plantilla_paso t JOIN df_desayunar_arasaac m ON t.id_pictograma=m.id_local
      WHERE t.id_plantilla_actividad<>plantilla) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Pictogramas compartidos por otras plantillas; requiere revision';
  END IF;
  -- No cambiar imágenes ya utilizadas por pasos reales. En una repetición con
  -- recursos ya migrados no se escribe nada y esas referencias pueden existir.
  IF EXISTS (SELECT 1 FROM paso_actividad a JOIN pictograma p ON p.id_pictograma=a.id_pictograma
      JOIN df_desayunar_arasaac m ON m.id_local=p.id_pictograma WHERE p.id_arasaac IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Un pictograma legacy ya se utiliza en pasos reales; revisar antes de migrar';
  END IF;

  UPDATE pictograma p JOIN df_desayunar_arasaac m ON p.id_pictograma=m.id_local
    SET p.id_arasaac=m.id_arasaac, p.fuente='ARASAAC', p.nombre=m.etiqueta, p.url_imagen=NULL
    WHERE p.id_arasaac IS NULL;
  IF (SELECT COUNT(*) FROM pictograma p JOIN df_desayunar_arasaac m
      ON p.id_pictograma=m.id_local AND p.id_arasaac=m.id_arasaac
      AND BINARY p.fuente=BINARY 'ARASAAC' AND BINARY p.nombre=BINARY m.etiqueta
      WHERE p.url_imagen IS NULL) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='No se verifico el resultado; backfill revertido';
  END IF;
  COMMIT;
  SELECT p.id_pictograma, p.nombre, p.id_arasaac, p.fuente, p.url_imagen
    FROM pictograma p JOIN df_desayunar_arasaac m ON m.id_local=p.id_pictograma ORDER BY p.id_pictograma;
  DROP TEMPORARY TABLE df_desayunar_arasaac;
END$$
CALL df_backfill_desayunar_arasaac()$$
DROP PROCEDURE df_backfill_desayunar_arasaac$$
DELIMITER ;
