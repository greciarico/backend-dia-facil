-- Incremental extension for an existing database. Review before applying.
-- Execute only if cuidador.avatar_id does not already exist.
ALTER TABLE cuidador ADD COLUMN avatar_id VARCHAR(32) NULL;
