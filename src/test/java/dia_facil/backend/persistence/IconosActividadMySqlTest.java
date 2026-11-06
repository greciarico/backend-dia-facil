package dia_facil.backend.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, EMPTY disposable diafacil_test_iconos_* database, never local credentials. */
@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_ICONOS_MYSQL_URL", matches = ".+")
class IconosActividadMySqlTest {
    @Test void scriptsRepetiblesPreservanPasosYBloqueanPerdidasAntesDeLimpiar() throws Exception {
        String url = System.getenv("DIAFACIL_TEST_ICONOS_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_iconos_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        try (var c = DriverManager.getConnection(url, System.getenv("DIAFACIL_TEST_ICONOS_MYSQL_USER"),
                System.getenv("DIAFACIL_TEST_ICONOS_MYSQL_PASSWORD"))) {
            assertTrue(c.getCatalog().startsWith("diafacil_test_iconos_"));
            assertEquals(0, number(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"), "Base exclusiva vacia requerida");
            assertEquals(0, number(c, "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE()"));
            // Own every object only after rejecting a nonempty database.
            try {
                fixture(c);
                script(c, "seed-iconos-actividad.sql"); script(c, "seed-iconos-actividad.sql");
                assertEquals(52, number(c, "SELECT COUNT(*) FROM icono_actividad"));
                execute(c, "UPDATE icono_actividad SET nombre='Etiqueta revisada' WHERE codigo='bread'");
                script(c, "seed-iconos-actividad.sql");
                assertEquals(1, number(c, "SELECT COUNT(*) FROM icono_actividad WHERE codigo='bread' AND nombre='Etiqueta revisada'"));
                script(c, "backfill-iconos-actividad.sql"); script(c, "backfill-iconos-actividad.sql");
                assertEquals(1, number(c, "SELECT COUNT(*) FROM actividad a JOIN icono_actividad i USING(id_icono_actividad) WHERE a.id_pictograma=47 AND i.codigo='bread'"));
                assertEquals(1, number(c, "SELECT COUNT(*) FROM plantilla_actividad t JOIN icono_actividad i USING(id_icono_actividad) WHERE t.nombre='Desayunar' AND i.codigo='bread'"));
                assertEquals(1, number(c, "SELECT COUNT(*) FROM palabra_clave_icono_actividad k JOIN icono_actividad i USING(id_icono_actividad) WHERE i.codigo='bread' AND k.palabra='merienda'"));
                assertEquals(10, number(c, "SELECT COUNT(*) FROM pictograma"));

                execute(c, "UPDATE actividad SET id_icono_actividad=(SELECT id_icono_actividad FROM icono_actividad WHERE codigo='bed')");
                assertThrows(SQLException.class, () -> script(c, "backfill-iconos-actividad.sql"));
                assertEquals(1, number(c, "SELECT COUNT(*) FROM actividad a JOIN icono_actividad i USING(id_icono_actividad) WHERE i.codigo='bed'"));
                execute(c, "UPDATE actividad SET id_pictograma=300,id_icono_actividad=NULL");
                assertThrows(SQLException.class, () -> script(c, "backfill-iconos-actividad.sql"));
                assertEquals(1, number(c, "SELECT COUNT(*) FROM actividad WHERE id_pictograma=300 AND id_icono_actividad IS NULL"));
                execute(c, "UPDATE actividad SET id_pictograma=47"); script(c, "backfill-iconos-actividad.sql");

                script(c, "cleanup-iconos-actividad.sql"); // creates routine; intentionally does NOT call it
                assertEquals(2, oldColumns(c)); assertEquals(10, number(c, "SELECT COUNT(*) FROM pictograma"));
                execute(c, "INSERT INTO paso_actividad(id_pictograma) VALUES(47)");
                assertThrows(SQLException.class, () -> execute(c, "CALL df_cleanup_iconos_actividad()"));
                assertEquals(2, oldColumns(c));
                execute(c, "DELETE FROM paso_actividad WHERE id_pictograma=47");
                execute(c, "INSERT INTO plantilla_paso(id_plantilla_actividad,id_pictograma,orden,descripcion) VALUES(9,47,6,'Conservar')");
                assertThrows(SQLException.class, () -> execute(c, "CALL df_cleanup_iconos_actividad()"));
                assertEquals(2, oldColumns(c));
                execute(c, "DELETE FROM plantilla_paso WHERE id_pictograma=47");
                execute(c, "DELETE k FROM palabra_clave_icono_actividad k JOIN icono_actividad i USING(id_icono_actividad) WHERE i.codigo='bread' AND k.palabra='merienda'");
                assertThrows(SQLException.class, () -> execute(c, "CALL df_cleanup_iconos_actividad()"));
                assertEquals(2, oldColumns(c)); script(c, "backfill-iconos-actividad.sql");
                execute(c, "CALL df_cleanup_iconos_actividad()");
                assertEquals(0, oldColumns(c)); assertEquals(5, number(c, "SELECT COUNT(*) FROM pictograma"));
                assertEquals(5, number(c, "SELECT COUNT(*) FROM plantilla_paso WHERE id_plantilla_actividad=9 AND id_pictograma=299+orden"));
                assertEquals(1, number(c, "SELECT COUNT(*) FROM palabra_clave_actividad WHERE id_plantilla_actividad=9 AND palabra='desayunar'"));
                assertEquals(1, number(c, "SELECT COUNT(*) FROM paso_actividad WHERE id_pictograma=300"));
                execute(c, "DELETE FROM icono_actividad WHERE codigo='bread'");
                assertEquals(1, number(c, "SELECT COUNT(*) FROM actividad WHERE id_icono_actividad IS NULL"));
                assertEquals(1, number(c, "SELECT COUNT(*) FROM plantilla_actividad WHERE id_icono_actividad IS NULL"));
                assertEquals(0, number(c, "SELECT COUNT(*) FROM palabra_clave_icono_actividad k LEFT JOIN icono_actividad i USING(id_icono_actividad) WHERE i.id_icono_actividad IS NULL"));
            } finally {
                for (String routine : new String[] {"df_cleanup_iconos_actividad", "df_backfill_iconos_actividad"}) execute(c, "DROP PROCEDURE IF EXISTS " + routine);
                for (String table : new String[] {"paso_actividad", "plantilla_paso", "palabra_clave_actividad", "actividad", "plantilla_actividad",
                        "palabra_clave_pictograma", "palabra_clave_icono_actividad", "pictograma", "icono_actividad"}) execute(c, "DROP TABLE IF EXISTS " + table);
            }
        }
    }

    private static int oldColumns(Connection c) throws SQLException {
        return number(c, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('actividad','plantilla_actividad') AND COLUMN_NAME='id_pictograma'");
    }
    private static int number(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement(); var rs = statement.executeQuery(sql)) { assertTrue(rs.next()); return rs.getInt(1); }
    }
    private static void execute(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement()) { statement.execute(sql); }
    }
    private static void script(Connection c, String file) throws Exception {
        String delimiter = ";"; var sql = new StringBuilder();
        for (String line : Files.readAllLines(Path.of("docs", "sql", file))) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            if (trimmed.startsWith("DELIMITER ")) { delimiter = trimmed.substring(10); continue; }
            sql.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String command = sql.toString().stripTrailing();
                execute(c, command.substring(0, command.length() - delimiter.length())); sql.setLength(0);
            }
        }
        assertTrue(sql.toString().isBlank(), "Incomplete SQL statement");
    }
    private static void fixture(Connection c) throws Exception {
        // Exact new catalog DDL, compact old fixtures exercising only migration columns.
        String reference = Files.readString(Path.of("..", "documentacion-dia-facil", "scrip_diafacil_v7.sql"));
        for (String table : new String[] {"icono_actividad", "palabra_clave_icono_actividad", "pictograma", "palabra_clave_pictograma"}) {
            var match = java.util.regex.Pattern.compile("CREATE TABLE " + table + " \\([\\s\\S]*?\\) ENGINE=InnoDB;").matcher(reference);
            assertTrue(match.find()); execute(c, match.group());
        }
        execute(c, "CREATE TABLE actividad(id_actividad INT PRIMARY KEY AUTO_INCREMENT,id_pictograma INT NULL,id_icono_actividad INT NULL, CONSTRAINT fk_actividad_pictograma FOREIGN KEY(id_pictograma) REFERENCES pictograma(id_pictograma) ON DELETE SET NULL, CONSTRAINT fk_actividad_icono FOREIGN KEY(id_icono_actividad) REFERENCES icono_actividad(id_icono_actividad) ON DELETE SET NULL) ENGINE=InnoDB");
        execute(c, "CREATE TABLE plantilla_actividad(id_plantilla_actividad INT PRIMARY KEY,nombre VARCHAR(80),id_pictograma INT NULL,id_icono_actividad INT NULL, CONSTRAINT fk_plantilla_pictograma FOREIGN KEY(id_pictograma) REFERENCES pictograma(id_pictograma) ON DELETE SET NULL, CONSTRAINT fk_plantilla_icono FOREIGN KEY(id_icono_actividad) REFERENCES icono_actividad(id_icono_actividad) ON DELETE SET NULL) ENGINE=InnoDB");
        execute(c, "CREATE TABLE paso_actividad(id_paso_actividad INT PRIMARY KEY AUTO_INCREMENT,id_pictograma INT NULL,FOREIGN KEY(id_pictograma) REFERENCES pictograma(id_pictograma) ON DELETE SET NULL) ENGINE=InnoDB");
        execute(c, "CREATE TABLE plantilla_paso(id_plantilla_paso INT PRIMARY KEY AUTO_INCREMENT,id_plantilla_actividad INT NOT NULL,id_pictograma INT NULL,orden INT,descripcion VARCHAR(120),FOREIGN KEY(id_plantilla_actividad) REFERENCES plantilla_actividad(id_plantilla_actividad),FOREIGN KEY(id_pictograma) REFERENCES pictograma(id_pictograma) ON DELETE SET NULL) ENGINE=InnoDB");
        execute(c, "CREATE TABLE palabra_clave_actividad(id_plantilla_actividad INT NOT NULL,palabra VARCHAR(60),FOREIGN KEY(id_plantilla_actividad) REFERENCES plantilla_actividad(id_plantilla_actividad)) ENGINE=InnoDB");
        execute(c, "INSERT INTO pictograma(id_pictograma,nombre,url_imagen) VALUES(47,'Pan','/pictogramas/pan.png'),(62,'Libros','/pictogramas/libros.png'),(93,'Almuerzo','/pictogramas/almuerzo.png'),(101,'Caminar','/pictogramas/caminar.png'),(205,'Dormir','/pictogramas/dormir.png'),(300,'Lavarse las manos','/pictogramas/lavar-manos.png'),(301,'Preparar la mesa','/pictogramas/mesa.png'),(302,'Servir la comida','/pictogramas/servir.png'),(303,'Comer','/pictogramas/comer.png'),(304,'Limpiar y recoger','/pictogramas/limpiar.png')");
        execute(c, "INSERT INTO palabra_clave_pictograma(id_pictograma,palabra) VALUES(47,'merienda'),(300,'manos')");
        execute(c, "INSERT INTO actividad(id_pictograma) VALUES(47)");
        execute(c, "INSERT INTO plantilla_actividad VALUES(9,'Desayunar',47,NULL)");
        execute(c, "INSERT INTO palabra_clave_actividad VALUES(9,'desayunar')");
        execute(c, "INSERT INTO paso_actividad(id_pictograma) VALUES(300)");
        execute(c, "INSERT INTO plantilla_paso(id_plantilla_actividad,id_pictograma,orden,descripcion) VALUES(9,300,1,'Lávate las manos'),(9,301,2,'Prepara la mesa'),(9,302,3,'Sirve la comida'),(9,303,4,'Come'),(9,304,5,'Limpia y recoge')");
    }
}
