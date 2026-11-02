-- B1: revision persistente por instalacion. REQUIERE APROBACION ANTES DE EJECUTAR EN MYSQL REAL.
-- Unica modificacion propuesta: crear esta tabla. No actualiza ni elimina datos existentes.
-- No ejecutar sobre una tabla preexistente sin comprobar antes su estructura.
CREATE TABLE control_registro_push (
    id_instalacion VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_instalacion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
