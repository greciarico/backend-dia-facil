-- Review and apply once; independent of perfil_usuario/dispositivo. Times are UTC.
CREATE TABLE sesion_cuidador (
  id_sesion BIGINT NOT NULL AUTO_INCREMENT,
  id_cuidador INT NOT NULL,
  creada_en DATETIME(6) NOT NULL,
  inactividad_hasta DATETIME(6) NOT NULL,
  vence_en DATETIME(6) NOT NULL,
  revocada_en DATETIME(6) NULL,
  PRIMARY KEY (id_sesion),
  KEY idx_sesion_cuidador (id_cuidador),
  CONSTRAINT fk_sesion_cuidador FOREIGN KEY (id_cuidador) REFERENCES cuidador(id_cuidador)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE credencial_sesion_cuidador (
  id_credencial BIGINT NOT NULL AUTO_INCREMENT,
  id_sesion BIGINT NOT NULL,
  generacion INT NOT NULL,
  access_hash BINARY(32) NOT NULL,
  refresh_hash BINARY(32) NOT NULL,
  access_hasta DATETIME(6) NOT NULL,
  consumida_en DATETIME(6) NULL,
  PRIMARY KEY (id_credencial),
  UNIQUE KEY uq_credencial_access (access_hash),
  UNIQUE KEY uq_credencial_refresh (refresh_hash),
  UNIQUE KEY uq_credencial_generacion (id_sesion, generacion),
  CONSTRAINT fk_credencial_sesion FOREIGN KEY (id_sesion) REFERENCES sesion_cuidador(id_sesion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
