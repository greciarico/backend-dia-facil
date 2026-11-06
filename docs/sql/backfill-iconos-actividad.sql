-- MANUAL DML migration, AFTER expansion verification and seed-iconos-actividad.sql.
-- Backup first; pause application/SQL writes throughout seed, backfill and cleanup.
-- Does not delete old columns, pictures or words. Uses exact legacy seed signatures.
-- Requires CREATE ROUTINE. Do not use a client configured to continue after errors.
DELIMITER $$
DROP PROCEDURE IF EXISTS df_backfill_iconos_actividad$$
CREATE PROCEDURE df_backfill_iconos_actividad()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS df_iconos_mapa;
    RESIGNAL;
  END;
  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Selecciona y verifica la base antes de migrar.';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('actividad', 'plantilla_actividad')
      AND COLUMN_NAME IN ('id_pictograma', 'id_icono_actividad')) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Se requieren ambas columnas antiguas y nuevas. Audita el esquema.';
  END IF;
  START TRANSACTION;
  CREATE TEMPORARY TABLE df_iconos_mapa (
    nombre VARCHAR(80) NOT NULL, url_antigua VARCHAR(255) NOT NULL,
    codigo VARCHAR(60) NOT NULL, id_pictograma INT NULL, id_icono_actividad INT NULL
  ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  INSERT INTO df_iconos_mapa (nombre, url_antigua, codigo) VALUES
    ('Pan', '/pictogramas/pan.png', 'bread'),
    ('Libros', '/pictogramas/libros.png', 'open-book'),
    ('Almuerzo', '/pictogramas/almuerzo.png', 'dish-of-food'),
    ('Caminar', '/pictogramas/caminar.png', 'walk'),
    ('Dormir', '/pictogramas/dormir.png', 'bed');
  UPDATE df_iconos_mapa m LEFT JOIN pictograma p ON BINARY p.nombre = BINARY m.nombre
      AND BINARY p.url_imagen = BINARY m.url_antigua AND p.fuente IS NULL
    LEFT JOIN icono_actividad i ON BINARY i.codigo = BINARY m.codigo
    SET m.id_pictograma = p.id_pictograma, m.id_icono_actividad = i.id_icono_actividad;
  IF EXISTS (SELECT 1 FROM df_iconos_mapa WHERE id_icono_actividad IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Faltan codigos del catalogo nuevo; ejecuta y verifica el seed.';
  END IF;
  IF EXISTS (SELECT 1 FROM actividad a LEFT JOIN df_iconos_mapa m ON a.id_pictograma = m.id_pictograma
      WHERE a.id_pictograma IS NOT NULL AND m.id_pictograma IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Actividad con pictograma antiguo desconocido. Requiere mapeo revisado.';
  END IF;
  IF EXISTS (SELECT 1 FROM plantilla_actividad t LEFT JOIN df_iconos_mapa m ON t.id_pictograma = m.id_pictograma
      WHERE t.id_pictograma IS NOT NULL AND m.id_pictograma IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Plantilla con pictograma antiguo desconocido. Requiere mapeo revisado.';
  END IF;
  IF EXISTS (SELECT 1 FROM actividad a JOIN df_iconos_mapa m ON a.id_pictograma = m.id_pictograma
      WHERE a.id_icono_actividad IS NOT NULL AND a.id_icono_actividad <> m.id_icono_actividad) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicto de icono en actividad; no se sobrescribe.';
  END IF;
  IF EXISTS (SELECT 1 FROM plantilla_actividad t JOIN df_iconos_mapa m ON t.id_pictograma = m.id_pictograma
      WHERE t.id_icono_actividad IS NOT NULL AND t.id_icono_actividad <> m.id_icono_actividad) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicto de icono en plantilla; no se sobrescribe.';
  END IF;

  INSERT INTO palabra_clave_icono_actividad (id_icono_actividad, palabra)
    SELECT m.id_icono_actividad, k.palabra FROM palabra_clave_pictograma k
    JOIN df_iconos_mapa m ON k.id_pictograma = m.id_pictograma
    WHERE NOT EXISTS (SELECT 1 FROM palabra_clave_icono_actividad n
      WHERE n.id_icono_actividad = m.id_icono_actividad AND n.palabra = k.palabra);
  UPDATE actividad a JOIN df_iconos_mapa m ON a.id_pictograma = m.id_pictograma
    SET a.id_icono_actividad = m.id_icono_actividad WHERE a.id_icono_actividad IS NULL;
  UPDATE plantilla_actividad t JOIN df_iconos_mapa m ON t.id_pictograma = m.id_pictograma
    SET t.id_icono_actividad = m.id_icono_actividad WHERE t.id_icono_actividad IS NULL;

  IF EXISTS (SELECT 1 FROM actividad a JOIN df_iconos_mapa m ON a.id_pictograma = m.id_pictograma
      WHERE NOT (a.id_icono_actividad <=> m.id_icono_actividad)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No se verifico el traslado completo de actividades.';
  END IF;
  IF EXISTS (SELECT 1 FROM plantilla_actividad t JOIN df_iconos_mapa m ON t.id_pictograma = m.id_pictograma
      WHERE NOT (t.id_icono_actividad <=> m.id_icono_actividad)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No se verifico el traslado completo de plantillas.';
  END IF;
  DROP TEMPORARY TABLE df_iconos_mapa;
  COMMIT;
  SELECT 'Traslado verificado. Datos y columnas antiguos conservados.' AS resultado;
END$$
CALL df_backfill_iconos_actividad()$$
DROP PROCEDURE df_backfill_iconos_actividad$$
DELIMITER ;
