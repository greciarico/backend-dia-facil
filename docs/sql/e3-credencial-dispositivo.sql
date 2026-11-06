-- E3: DDL exclusivamente aditivo, de revisión/aplicación manual.
-- No ejecutado automáticamente. Inspeccionar antes si la tabla ya existe.
-- No ejecutar el SQL v7 completo sobre una BD existente.
CREATE TABLE credencial_dispositivo (
  id_credencial_dispositivo BIGINT NOT NULL AUTO_INCREMENT,
  token_hash BINARY(32) NOT NULL,
  id_dispositivo INT NULL,
  preparacion_hasta DATETIME(6) NULL,
  PRIMARY KEY (id_credencial_dispositivo),
  UNIQUE KEY uq_dispositivo_token (token_hash),
  UNIQUE KEY uq_credencial_dispositivo (id_dispositivo),
  KEY idx_preparacion_dispositivo (preparacion_hasta),
  CONSTRAINT fk_credencial_dispositivo FOREIGN KEY (id_dispositivo)
    REFERENCES dispositivo (id_dispositivo) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
