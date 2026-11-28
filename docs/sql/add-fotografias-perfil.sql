-- DF-13.2. Aplicar una sola vez antes de desplegar; ensayar en MySQL aislado.
ALTER TABLE cuidador ADD COLUMN foto_referencia VARCHAR(36) NULL;
ALTER TABLE perfil_usuario ADD COLUMN foto_referencia VARCHAR(36) NULL;

CREATE TABLE foto_perfil_operacion (
    referencia VARCHAR(36) PRIMARY KEY,
    entorno VARCHAR(4) NOT NULL,
    destino VARCHAR(12) NOT NULL,
    limpiar_desde DATETIME NOT NULL,
    INDEX idx_foto_limpieza (entorno, limpiar_desde)
) ENGINE=InnoDB;
