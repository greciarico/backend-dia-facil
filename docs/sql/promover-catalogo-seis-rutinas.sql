-- Transición explícita de la curación aprobada, NO seeder ni tarea de arranque.
-- Detener TODAS las instancias del backend, respaldar y seleccionar la BD revisada.
-- Aplicar junto al nuevo datos-base.json, ANTES de reiniciar. No continuar ante errores.
-- Requiere las cinco plantillas anteriores y los pictogramas ya persistidos por la
-- curación. No inserta pictogramas, no consulta actividades de usuario ni cambia sus pasos.
-- El JSON sigue siendo la única fuente ejecutable del inicializador. Este mapa
-- documenta únicamente la transición histórica de referencias null a IDs aprobados.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DELIMITER $$
DROP PROCEDURE IF EXISTS df_promover_catalogo_seis_rutinas$$
CREATE PROCEDURE df_promover_catalogo_seis_rutinas()
BEGIN
  DECLARE referencias_actualizadas INT DEFAULT 0;
  DECLARE keywords_retiradas INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS df_curacion_seis_rutinas;
    RESIGNAL;
  END;
  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Selecciona y verifica la BD antes de promover la curacion';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME IN ('pictograma','plantilla_actividad','plantilla_paso','icono_actividad','palabra_clave_icono_actividad')
      AND ENGINE='InnoDB') <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Falta estructura InnoDB esperada';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='pictograma' AND INDEX_NAME='uq_pictograma_arasaac') <> 1
      OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='pictograma' AND INDEX_NAME='uq_pictograma_arasaac'
        AND COLUMN_NAME='id_arasaac' AND NON_UNIQUE=0 AND SEQ_IN_INDEX=1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Falta UNIQUE id_arasaac esperado';
  END IF;

  START TRANSACTION;
  CREATE TEMPORARY TABLE df_curacion_seis_rutinas (
    plantilla VARCHAR(80) NOT NULL, icono VARCHAR(60) NOT NULL,
    orden INT NOT NULL, descripcion VARCHAR(120) NOT NULL,
    id_arasaac INT NOT NULL, anterior_null BOOLEAN NOT NULL,
    PRIMARY KEY(plantilla,orden)
  ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  INSERT INTO df_curacion_seis_rutinas VALUES
    ('Ir al colegio','school',1,'Prepara tu mochila',8166,TRUE),
    ('Ir al colegio','school',2,'Ponte los zapatos',17026,TRUE),
    ('Ir al colegio','school',3,'Ponte la mochila',37518,TRUE),
    ('Ir al colegio','school',4,'Ve al colegio',37036,TRUE),
    ('Estudiar','open-book',1,'Prepara tus materiales',6640,TRUE),
    ('Estudiar','open-book',2,'Abre el cuaderno',2359,TRUE),
    ('Estudiar','open-book',3,'Haz la tarea',32814,TRUE),
    ('Estudiar','open-book',4,'Guarda tus materiales',6474,TRUE),
    ('Almorzar','dish-of-food',1,'Lávate las manos',2443,FALSE),
    ('Almorzar','dish-of-food',2,'Siéntate a la mesa',38944,TRUE),
    ('Almorzar','dish-of-food',3,'Come',2349,FALSE),
    ('Almorzar','dish-of-food',4,'Recoge tu plato',17298,TRUE),
    ('Cenar','food',1,'Lávate las manos',2443,FALSE),
    ('Cenar','food',2,'Siéntate a la mesa',38944,TRUE),
    ('Cenar','food',3,'Come',2349,FALSE),
    ('Cenar','food',4,'Recoge tu plato',17298,TRUE),
    ('Prepararse para dormir','bed',1,'Ponte el pijama',2522,TRUE),
    ('Prepararse para dormir','bed',2,'Cepíllate los dientes',2326,TRUE),
    ('Prepararse para dormir','bed',3,'Acuéstate',2369,TRUE);

  SELECT t.id_plantilla_actividad FROM plantilla_actividad t
    WHERE t.nombre IN (SELECT plantilla FROM df_curacion_seis_rutinas)
    ORDER BY t.id_plantilla_actividad FOR UPDATE;
  SELECT s.id_plantilla_paso FROM plantilla_paso s JOIN plantilla_actividad t USING(id_plantilla_actividad)
    WHERE t.nombre IN (SELECT plantilla FROM df_curacion_seis_rutinas)
    ORDER BY s.id_plantilla_paso FOR UPDATE;
  SELECT p.id_pictograma FROM pictograma p
    WHERE p.id_arasaac IN (SELECT id_arasaac FROM df_curacion_seis_rutinas)
    ORDER BY p.id_pictograma FOR UPDATE;
  SELECT i.id_icono_actividad FROM icono_actividad i WHERE i.codigo='food' FOR UPDATE;

  IF (SELECT COUNT(*) FROM plantilla_actividad t JOIN icono_actividad i USING(id_icono_actividad)
      JOIN (SELECT DISTINCT plantilla,icono FROM df_curacion_seis_rutinas) m
        ON BINARY t.nombre=BINARY m.plantilla AND BINARY i.codigo=BINARY m.icono) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Faltan plantillas o difieren sus nombres/iconos; no se corrige';
  END IF;
  IF (SELECT COUNT(*) FROM pictograma p WHERE p.id_arasaac IN (SELECT id_arasaac FROM df_curacion_seis_rutinas)
      AND BINARY p.fuente=BINARY 'ARASAAC' AND p.url_imagen IS NULL) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Faltan pictogramas curados compatibles; no se insertan sustitutos';
  END IF;
  IF (SELECT COUNT(*) FROM plantilla_paso s JOIN plantilla_actividad t USING(id_plantilla_actividad)
      WHERE t.nombre IN (SELECT plantilla FROM df_curacion_seis_rutinas)) <> 19 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cantidad de pasos distinta del mapa aprobado; revisar sin sobrescribir';
  END IF;
  IF EXISTS (SELECT m.plantilla,m.orden FROM df_curacion_seis_rutinas m
      LEFT JOIN plantilla_actividad t ON BINARY t.nombre=BINARY m.plantilla
      LEFT JOIN plantilla_paso s ON s.id_plantilla_actividad=t.id_plantilla_actividad AND s.orden=m.orden
      GROUP BY m.plantilla,m.orden HAVING COUNT(s.id_plantilla_paso)<>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Faltan posiciones unicas por plantilla; no se corrige';
  END IF;
  IF (SELECT COUNT(*) FROM plantilla_paso s JOIN plantilla_actividad t USING(id_plantilla_actividad)
        JOIN df_curacion_seis_rutinas m ON BINARY t.nombre=BINARY m.plantilla
          AND s.orden=m.orden AND BINARY s.descripcion=BINARY m.descripcion) <> 19 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Pasos/textos/ordenes distintos del mapa aprobado; revisar sin sobrescribir';
  END IF;
  IF EXISTS (SELECT 1 FROM plantilla_paso s JOIN plantilla_actividad t USING(id_plantilla_actividad)
      JOIN df_curacion_seis_rutinas m ON BINARY t.nombre=BINARY m.plantilla AND s.orden=m.orden
      LEFT JOIN pictograma p ON p.id_pictograma=s.id_pictograma
      WHERE ((s.id_pictograma IS NULL AND m.anterior_null)
        OR (p.id_arasaac=m.id_arasaac AND BINARY p.fuente=BINARY 'ARASAAC')) IS NOT TRUE) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Referencia previa inesperada; no se reemplaza un pictograma diferente';
  END IF;

  UPDATE plantilla_paso s JOIN plantilla_actividad t USING(id_plantilla_actividad)
    JOIN df_curacion_seis_rutinas m ON BINARY t.nombre=BINARY m.plantilla AND s.orden=m.orden
    JOIN pictograma p ON p.id_arasaac=m.id_arasaac
    SET s.id_pictograma=p.id_pictograma WHERE s.id_pictograma IS NULL;
  SET referencias_actualizadas=ROW_COUNT();
  DELETE k FROM palabra_clave_icono_actividad k JOIN icono_actividad i USING(id_icono_actividad)
    WHERE i.codigo='food' AND k.palabra IN ('cena','cenar','cenando');
  SET keywords_retiradas=ROW_COUNT();
  IF (SELECT COUNT(*) FROM plantilla_paso s JOIN plantilla_actividad t USING(id_plantilla_actividad)
      JOIN df_curacion_seis_rutinas m ON BINARY t.nombre=BINARY m.plantilla AND s.orden=m.orden
      JOIN pictograma p ON p.id_pictograma=s.id_pictograma AND p.id_arasaac=m.id_arasaac) <> 19
      OR EXISTS (SELECT 1 FROM palabra_clave_icono_actividad k JOIN icono_actividad i USING(id_icono_actividad)
        WHERE i.codigo='food' AND k.palabra IN ('cena','cenar','cenando')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='No se verifico el resultado; transicion revertida';
  END IF;
  COMMIT;
  DROP TEMPORARY TABLE df_curacion_seis_rutinas;
  SELECT referencias_actualizadas,keywords_retiradas;
END$$
CALL df_promover_catalogo_seis_rutinas()$$
DROP PROCEDURE df_promover_catalogo_seis_rutinas$$
DELIMITER ;
