-- DF-11. Run with the backend stopped on the DF-09 schema, after backup.
-- Set @df11_fecha_inicio to the deployment day in diafacil.agenda.zone before sourcing this script.
-- Run beginning and snapshot in the same agenda day; if rollout crosses midnight, repeat the snapshot before opening writes.
CREATE TABLE seguimiento_cobertura (
  id INT PRIMARY KEY,
  fecha_inicio DATE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE seguimiento_rutina (
  id_perfil_usuario INT NOT NULL,
  id_actividad INT NOT NULL,
  fecha DATE NOT NULL,
  revision INT NOT NULL,
  nombre VARCHAR(80) NOT NULL,
  hora TIME NOT NULL,
  codigo_icono VARCHAR(60) NULL,
  pasos JSON NOT NULL,
  retirada TINYINT(1) NOT NULL,
  PRIMARY KEY (id_perfil_usuario, id_actividad, fecha),
  CONSTRAINT fk_seguimiento_perfil FOREIGN KEY (id_perfil_usuario) REFERENCES perfil_usuario(id_perfil_usuario) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

START TRANSACTION;
INSERT INTO seguimiento_cobertura (id,fecha_inicio) VALUES (1,@df11_fecha_inicio);
INSERT INTO seguimiento_rutina (id_perfil_usuario,id_actividad,fecha,revision,nombre,hora,codigo_icono,pasos,retirada)
SELECT a.id_perfil_usuario,a.id_actividad,@df11_fecha_inicio,a.revision,a.nombre,a.hora,i.codigo,
    COALESCE((
      SELECT JSON_OBJECTAGG(p.id_paso_actividad, JSON_OBJECT(
        'idPasoActividad',p.id_paso_actividad,'orden',p.orden,'descripcion',p.descripcion,'idPictograma',g.id_pictograma,
        'nombrePictograma',g.nombre,'idArasaac',g.id_arasaac))
      FROM paso_actividad p LEFT JOIN pictograma g ON g.id_pictograma=p.id_pictograma
      WHERE p.id_actividad=a.id_actividad AND p.revision=a.revision AND p.activo=1
    ),JSON_OBJECT()),0
FROM actividad a LEFT JOIN icono_actividad i ON i.id_icono_actividad=a.id_icono_actividad
WHERE a.estado='ACTIVA';
COMMIT;
