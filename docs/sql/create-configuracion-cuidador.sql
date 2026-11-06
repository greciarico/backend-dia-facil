-- Transicion ADITIVA y manual. No ejecutar automaticamente al arrancar.
-- 1. Elegir/verificar el esquema correcto antes de ejecutar ningun CREATE.
SELECT DATABASE() AS esquema;
SELECT table_name FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name IN ('cuidador','configuracion_cuidador');
-- Si configuracion_cuidador YA existe, DETENERSE y comparar SHOW CREATE TABLE;
-- no recrearla ni ocultar diferencias mediante IF NOT EXISTS.
-- 2. Solo si falta la tabla y cuidador existe con PK INT:
CREATE TABLE configuracion_cuidador (
  id_cuidador INT NOT NULL,
  tamano_texto ENUM('PEQUENO','MEDIANO','GRANDE') NOT NULL DEFAULT 'MEDIANO',
  alto_contraste TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id_cuidador),
  CONSTRAINT fk_config_cuidador FOREIGN KEY (id_cuidador)
    REFERENCES cuidador (id_cuidador) ON DELETE CASCADE,
  CONSTRAINT chk_config_cuidador_contraste CHECK (alto_contraste IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 3. Verificar PK compartida, FK, defaults y CHECK. Cero filas es correcto:
-- no se necesita backfill, seed ni cambios de cuidadores/perfiles existentes.
SHOW CREATE TABLE configuracion_cuidador;
SELECT COUNT(*) AS configuraciones FROM configuracion_cuidador;
