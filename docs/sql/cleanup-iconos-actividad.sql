-- DESTRUCTIVE, MANUAL, SEPARATE APPROVAL AFTER inspecting backup/audit/backfill.
-- Never run automatically. Pause ALL writes and stop on the first error.
-- MySQL DDL commits implicitly: ROLLBACK cannot undo the ALTER TABLE statements.
-- Expected transitional schema: both old columns/FKs and new columns exist.
DELIMITER $$
DROP PROCEDURE IF EXISTS df_cleanup_iconos_actividad$$
CREATE PROCEDURE df_cleanup_iconos_actividad()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS df_iconos_limpieza;
    RESIGNAL;
  END;
  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Selecciona y verifica la base antes de limpiar.';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('actividad', 'plantilla_actividad')
      AND COLUMN_NAME IN ('id_pictograma', 'id_icono_actividad')) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Esquema inesperado o limpieza parcial; revisa antes de continuar.';
  END IF;
  -- Exact expected FKs; an evolved/different local schema requires manual review.
  IF (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE()
      AND ((TABLE_NAME = 'actividad' AND CONSTRAINT_NAME = 'fk_actividad_pictograma')
        OR (TABLE_NAME = 'plantilla_actividad' AND CONSTRAINT_NAME = 'fk_plantilla_pictograma'))
      AND COLUMN_NAME = 'id_pictograma' AND REFERENCED_TABLE_NAME = 'pictograma'
      AND REFERENCED_COLUMN_NAME = 'id_pictograma') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FK antiguas distintas de las verificadas; no se altera el esquema.';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
      WHERE REFERENCED_TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'pictograma'
      AND NOT (TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'id_pictograma' AND REFERENCED_COLUMN_NAME = 'id_pictograma'
        AND TABLE_NAME IN ('actividad','plantilla_actividad','paso_actividad','plantilla_paso','palabra_clave_pictograma'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Referencias a pictograma fuera del modelo revisado; no se limpia.';
  END IF;
  CREATE TEMPORARY TABLE df_iconos_limpieza (
    nombre VARCHAR(80) NOT NULL, url_antigua VARCHAR(255) NOT NULL,
    codigo VARCHAR(60) NOT NULL, id_pictograma INT NULL, id_icono_actividad INT NULL
  ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  INSERT INTO df_iconos_limpieza (nombre, url_antigua, codigo) VALUES
    ('Pan', '/pictogramas/pan.png', 'bread'),
    ('Libros', '/pictogramas/libros.png', 'open-book'),
    ('Almuerzo', '/pictogramas/almuerzo.png', 'dish-of-food'),
    ('Caminar', '/pictogramas/caminar.png', 'walk'),
    ('Dormir', '/pictogramas/dormir.png', 'bed');
  UPDATE df_iconos_limpieza m LEFT JOIN pictograma p ON BINARY p.nombre = BINARY m.nombre
      AND BINARY p.url_imagen = BINARY m.url_antigua AND p.fuente IS NULL
    LEFT JOIN icono_actividad i ON BINARY i.codigo = BINARY m.codigo
    SET m.id_pictograma = p.id_pictograma, m.id_icono_actividad = i.id_icono_actividad;
  IF EXISTS (SELECT 1 FROM df_iconos_limpieza WHERE id_icono_actividad IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Catalogo de destino incompleto; no se limpia.';
  END IF;
  IF EXISTS (SELECT 1 FROM actividad a LEFT JOIN df_iconos_limpieza m ON a.id_pictograma = m.id_pictograma
      WHERE a.id_pictograma IS NOT NULL AND (m.id_pictograma IS NULL OR NOT (a.id_icono_actividad <=> m.id_icono_actividad))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Actividad sin traslado completo o con conflicto; no se limpia.';
  END IF;
  IF EXISTS (SELECT 1 FROM plantilla_actividad t LEFT JOIN df_iconos_limpieza m ON t.id_pictograma = m.id_pictograma
      WHERE t.id_pictograma IS NOT NULL AND (m.id_pictograma IS NULL OR NOT (t.id_icono_actividad <=> m.id_icono_actividad))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Plantilla sin traslado completo o con conflicto; no se limpia.';
  END IF;
  IF EXISTS (SELECT 1 FROM palabra_clave_pictograma k JOIN df_iconos_limpieza m ON k.id_pictograma = m.id_pictograma
      WHERE NOT EXISTS (SELECT 1 FROM palabra_clave_icono_actividad n
        WHERE n.id_icono_actividad = m.id_icono_actividad AND n.palabra = k.palabra)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Palabras clave pendientes de traslado; no se limpia.';
  END IF;
  IF EXISTS (SELECT 1 FROM paso_actividad s JOIN df_iconos_limpieza m ON s.id_pictograma = m.id_pictograma) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Recurso antiguo usado por un paso real; conservar y revisar.';
  END IF;
  IF EXISTS (SELECT 1 FROM plantilla_paso s JOIN df_iconos_limpieza m ON s.id_pictograma = m.id_pictograma) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Recurso antiguo usado por un paso de plantilla; conservar y revisar.';
  END IF;
  -- Nothing above has deleted user data or altered permanent tables.
  ALTER TABLE actividad DROP FOREIGN KEY fk_actividad_pictograma, DROP COLUMN id_pictograma;
  ALTER TABLE plantilla_actividad DROP FOREIGN KEY fk_plantilla_pictograma, DROP COLUMN id_pictograma;
  START TRANSACTION;
  -- Deletes only the five legacy signatures, never the action pictograms.
  -- Their keyword rows cascade; both step tables were checked before the DDL.
  DELETE p FROM pictograma p JOIN df_iconos_limpieza m ON p.id_pictograma = m.id_pictograma;
  COMMIT;
  DROP TEMPORARY TABLE df_iconos_limpieza;
  SELECT 'Limpieza finalizada. Verificar SHOW CREATE TABLE y los cinco pasos.' AS resultado;
END$$
-- Deliberate second gate: uncomment ONLY after separate approval of the audit results.
-- CALL df_cleanup_iconos_actividad()$$
-- DROP PROCEDURE df_cleanup_iconos_actividad$$
DELIMITER ;
