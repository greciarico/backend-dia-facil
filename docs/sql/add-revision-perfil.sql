-- DF-13.3. Aplicar una sola vez después de add-fotografias-perfil.sql.
ALTER TABLE perfil_usuario ADD COLUMN revision_perfil INT NOT NULL DEFAULT 1;
