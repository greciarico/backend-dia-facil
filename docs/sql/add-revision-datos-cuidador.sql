-- DF-13.4. Aplicar una sola vez después de add-fotografias-perfil.sql.
ALTER TABLE cuidador ADD COLUMN revision_datos_personales INT NOT NULL DEFAULT 1;
