-- DF-09. Run once on a backed-up database with the backend stopped.
-- Before running, the following audits must return zero rows. Resolve any findings manually.
SELECT g.id_progreso_paso FROM progreso_paso g
JOIN ejecucion_actividad e ON e.id_ejecucion_actividad=g.id_ejecucion_actividad
JOIN paso_actividad p ON p.id_paso_actividad=g.id_paso_actividad
WHERE p.id_actividad<>e.id_actividad OR p.activo<>1;
SELECT s.id_solicitud_ayuda FROM solicitud_ayuda s
JOIN ejecucion_actividad e ON e.id_ejecucion_actividad=s.id_ejecucion_actividad
JOIN paso_actividad p ON p.id_paso_actividad=s.id_paso_actividad
WHERE p.id_actividad<>e.id_actividad OR s.id_actividad<>e.id_actividad;

CREATE TABLE version_actividad (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_actividad INT NOT NULL,
  numero INT NOT NULL,
  nombre VARCHAR(80) NOT NULL,
  hora TIME NOT NULL,
  id_icono_actividad INT NULL,
  CONSTRAINT uq_version_actividad_numero UNIQUE (id_actividad, numero),
  CONSTRAINT fk_version_actividad FOREIGN KEY (id_actividad) REFERENCES actividad(id_actividad) ON DELETE CASCADE,
  CONSTRAINT fk_version_icono FOREIGN KEY (id_icono_actividad) REFERENCES icono_actividad(id_icono_actividad) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO version_actividad (id_actividad,numero,nombre,hora,id_icono_actividad)
SELECT id_actividad,1,nombre,hora,id_icono_actividad FROM actividad;

ALTER TABLE actividad ADD COLUMN revision INT NOT NULL DEFAULT 1;
ALTER TABLE paso_actividad ADD COLUMN revision INT NOT NULL DEFAULT 1,
  ADD CONSTRAINT fk_paso_version_actividad FOREIGN KEY (id_actividad, revision) REFERENCES version_actividad(id_actividad, numero) ON DELETE CASCADE;
ALTER TABLE ejecucion_actividad ADD COLUMN revision INT NOT NULL DEFAULT 1,
  ADD CONSTRAINT fk_ejecucion_version_actividad FOREIGN KEY (id_actividad, revision) REFERENCES version_actividad(id_actividad, numero) ON DELETE CASCADE;
