-- Incremento 2. REVIEW ONLY. Never executed by the application.
-- Optional manual reference; development uses approved Hibernate ddl-auto=update.
-- Do NOT execute alongside update. The normal path is isolated validation, backup,
-- explicit approval, then one controlled backend start. No real schema changes authorized here.
-- Back up schema/data. MySQL DDL auto-commits; do not treat this as an atomic migration.
-- Read-only preflight (expect columns absent and existing tokens inactive/unbound after migration):
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name IN ('token_push','entrega_push','sesion_cuidador');
SELECT COUNT(*) AS tokens_existentes FROM token_push;
-- No DELETE or reassignment of historical token ownership.
ALTER TABLE token_push
 ADD COLUMN id_instalacion VARCHAR(36) NULL,
 ADD COLUMN id_sesion BIGINT NULL,
 ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 0,
 ADD COLUMN generacion BIGINT NOT NULL DEFAULT 1,
 ADD COLUMN proyecto_eas VARCHAR(36) NULL,
 ADD CONSTRAINT uq_token_instalacion UNIQUE (id_instalacion),
 ADD CONSTRAINT fk_token_sesion FOREIGN KEY (id_sesion) REFERENCES sesion_cuidador(id_sesion) ON DELETE SET NULL;
CREATE TABLE entrega_push (
 id_entrega BIGINT NOT NULL AUTO_INCREMENT,
 id_notificacion_cuidador INT NOT NULL,
 id_token_push INT NOT NULL,
 generacion BIGINT NOT NULL,
 estado VARCHAR(32) NOT NULL,
 intentos INT NOT NULL,
 proxima_en DATETIME(6) NOT NULL,
 vence_en DATETIME(6) NOT NULL,
 bloqueo_hasta DATETIME(6) NULL,
 arrendamiento VARCHAR(36) NULL,
 ticket VARCHAR(255) NULL,
 error_codigo VARCHAR(64) NULL,
 PRIMARY KEY(id_entrega),
 CONSTRAINT uq_entrega_destino UNIQUE(id_notificacion_cuidador,id_token_push,generacion),
 INDEX idx_entrega_pendiente(estado,proxima_en),
 CONSTRAINT fk_entrega_notificacion FOREIGN KEY(id_notificacion_cuidador) REFERENCES notificacion_cuidador(id_notificacion_cuidador) ON DELETE CASCADE,
 CONSTRAINT fk_entrega_token FOREIGN KEY(id_token_push) REFERENCES token_push(id_token_push) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Verify SHOW CREATE TABLE for both tables before enabling push.
-- Recovery: disable push and stop backend. Preserve/export entrega_push before reverting.
-- Prefer forward correction after partial DDL; restore pre-migration backup only with explicit approval.
-- Never run DROP statements automatically; reverting columns loses device/session registration history.
