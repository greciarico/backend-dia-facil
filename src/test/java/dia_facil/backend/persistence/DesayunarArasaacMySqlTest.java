package dia_facil.backend.persistence;

import dia_facil.backend.persistence.repository.JpaDatosBaseStore;
import dia_facil.backend.service.datosbase.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: EMPTY disposable database only. Never reads application-local credentials. */
@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_ARASAAC_MYSQL_URL", matches = ".+")
class DesayunarArasaacMySqlTest {
    @Test void backfillRechazaConflictosConservaFkYUsuariosYEsIdempotente() throws Exception {
        String url = System.getenv("DIAFACIL_TEST_ARASAAC_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_arasaac_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        var ds = new DriverManagerDataSource(url, System.getenv("DIAFACIL_TEST_ARASAAC_MYSQL_USER"), System.getenv("DIAFACIL_TEST_ARASAAC_MYSQL_PASSWORD"));
        try (var c = ds.getConnection()) {
            assertTrue(c.getCatalog().startsWith("diafacil_test_arasaac_"));
            assertEquals(0, numero(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"));
            assertEquals(0, numero(c, "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE()"));
            var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource", ds)
                .applySetting("hibernate.hbm2ddl.auto", "update").build();
            try {
                var sources = new MetadataSources(registry); PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
                try (var factory = sources.buildMetadata().buildSessionFactory()) {
                    fixture(c);
                    var referencias = filas(c, "plantilla_paso");
                    for (var caso : List.of(
                        new String[]{"UPDATE pictograma SET url_imagen=NULL WHERE id_pictograma=2", "UPDATE pictograma SET url_imagen='/pictogramas/lavar-manos.png' WHERE id_pictograma=2"},
                        new String[]{"UPDATE pictograma SET nombre='Revisado' WHERE id_pictograma=2", "UPDATE pictograma SET nombre='Lavarse las manos' WHERE id_pictograma=2"},
                        new String[]{"UPDATE pictograma SET id_arasaac=999 WHERE id_pictograma=2", "UPDATE pictograma SET id_arasaac=NULL WHERE id_pictograma=2"},
                        new String[]{"INSERT INTO pictograma(id_pictograma,nombre,fuente,id_arasaac) VALUES(20,'Otro','ARASAAC',2443)", "DELETE FROM pictograma WHERE id_pictograma=20"},
                        new String[]{"UPDATE plantilla_paso SET descripcion='Texto revisado' WHERE orden=1", "UPDATE plantilla_paso SET descripcion='Lávate las manos' WHERE orden=1"},
                        new String[]{"UPDATE paso_actividad SET id_pictograma=2", "UPDATE paso_actividad SET id_pictograma=NULL"})) {
                        ejecutar(c, caso[0]); var antes = snapshot(c);
                        assertThrows(SQLException.class, () -> script(c));
                        assertEquals(antes, snapshot(c)); // All-or-nothing, no repair or user writes.
                        ejecutar(c, caso[1]);
                    }
                    var usuarios = List.of(filas(c, "cuidador"), filas(c, "perfil_usuario"), filas(c, "actividad"), filas(c, "paso_actividad"));
                    script(c); var convertido = snapshot(c); script(c); assertEquals(convertido, snapshot(c));
                    assertEquals(referencias, filas(c, "plantilla_paso"));
                    assertEquals(5, numero(c, "SELECT COUNT(*) FROM pictograma WHERE fuente='ARASAAC' AND url_imagen IS NULL"));
                    try (var s = c.createStatement(); var r = s.executeQuery("SELECT id_arasaac FROM pictograma ORDER BY id_pictograma")) {
                        for (int id : new int[]{2443,5549,36965,2349,8688}) { assertTrue(r.next()); assertEquals(id, r.getInt(1)); }
                        assertFalse(r.next());
                    }
                    var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
                    var manager = new JpaTransactionManager(factory);
                    var datos = LectorDatosBase.leer(JsonMapper.builder().build(), new ClassPathResource("db/datos-base.json"));
                    var sync = new SincronizadorDatosBase(new JpaDatosBaseStore(em));
                    var initializer = new InicializadorDatosBase(datos, sync, new VerificadorEsquemaDatosBase(em), manager);
                    assertTrue(initializer.inicializar().conflictos().isEmpty());
                    var again = initializer.inicializar(); assertEquals(0, again.creados()); assertTrue(again.conflictos().isEmpty());
                    assertEquals(5, numero(c, "SELECT COUNT(*) FROM pictograma"));
                    assertEquals(referencias, filas(c, "plantilla_paso"));
                    assertEquals(usuarios, List.of(filas(c, "cuidador"), filas(c, "perfil_usuario"), filas(c, "actividad"), filas(c, "paso_actividad")));
                    // Once migrated, a later real reference does not make a no-op repeat unsafe.
                    ejecutar(c, "UPDATE paso_actividad SET id_pictograma=2"); var beforeRepeat = snapshot(c);
                    script(c); assertEquals(beforeRepeat, snapshot(c));
                }
            } finally {
                org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry);
                ejecutar(c, "DROP PROCEDURE IF EXISTS df_backfill_desayunar_arasaac");
                for (var entity : PersistenceModelTest.ENTITIES.reversed())
                    ejecutar(c, "DROP TABLE IF EXISTS " + entity.getAnnotation(jakarta.persistence.Table.class).name());
            }
        }
    }
    private static void fixture(Connection c) throws Exception {
        ejecutar(c, "INSERT INTO icono_actividad(id_icono_actividad,codigo,nombre) VALUES(70,'bread','Pan')");
        ejecutar(c, "INSERT INTO plantilla_actividad(id_plantilla_actividad,nombre,id_icono_actividad) VALUES(90,'Desayunar',70)");
        var nombres = List.of("Lavarse las manos","Preparar la mesa","Servir la comida","Comer","Limpiar y recoger");
        var urls = List.of("lavar-manos","mesa","servir","comer","limpiar");
        var pasos = List.of("Lávate las manos","Prepara la mesa","Sirve la comida","Come","Limpia y recoge");
        for (int i=0;i<5;i++) {
            try (var s = c.prepareStatement("INSERT INTO pictograma(id_pictograma,nombre,url_imagen) VALUES(?,?,?)")) {
                s.setInt(1,i+2); s.setString(2,nombres.get(i)); s.setString(3,"/pictogramas/"+urls.get(i)+".png"); s.executeUpdate();
            }
            try (var s = c.prepareStatement("INSERT INTO plantilla_paso(id_plantilla_actividad,id_pictograma,orden,descripcion) VALUES(90,?,?,?)")) {
                s.setInt(1,i+2); s.setInt(2,i+1); s.setString(3,pasos.get(i)); s.executeUpdate();
            }
        }
        ejecutar(c, "INSERT INTO cuidador(id_cuidador,nombre,email) VALUES(100,'Prueba','arasaac@example.invalid')");
        ejecutar(c, "INSERT INTO perfil_usuario(id_perfil_usuario,id_cuidador,nombre,codigo_vinculacion) VALUES(100,100,'Persona','DF-987654')");
        ejecutar(c, "INSERT INTO actividad(id_actividad,id_perfil_usuario,nombre,hora) VALUES(100,100,'Rutina propia','08:00:00')");
        ejecutar(c, "INSERT INTO paso_actividad(id_actividad,orden,descripcion) VALUES(100,1,'Paso propio')");
    }
    private static Map<String,List<String>> snapshot(Connection c) throws SQLException {
        var rows = new LinkedHashMap<String,List<String>>();
        for (var table : List.of("pictograma","plantilla_paso","cuidador","perfil_usuario","actividad","paso_actividad")) rows.put(table, filas(c,table));
        return rows;
    }
    private static List<String> filas(Connection c, String table) throws SQLException {
        var rows = new ArrayList<String>();
        try (var s=c.createStatement(); var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1")) {
            while(r.next()) { var row=new Object[r.getMetaData().getColumnCount()]; for(int i=0;i<row.length;i++) row[i]=r.getObject(i+1); rows.add(Arrays.deepToString(row)); }
        }
        return rows;
    }
    private static long numero(Connection c,String sql) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)) { assertTrue(r.next());return r.getLong(1); }
    }
    private static void ejecutar(Connection c,String sql) throws SQLException { try(var s=c.createStatement()) { s.execute(sql); } }
    private static void script(Connection c) throws Exception {
        var text=new StringBuilder(); String delimiter=";";
        for(String line:Files.readAllLines(Path.of("docs/sql/backfill-desayunar-arasaac.sql"))) {
            String clean=line.strip(); if(clean.isEmpty()||clean.startsWith("--")) continue;
            if(clean.startsWith("DELIMITER ")) { delimiter=clean.substring(10);continue; }
            text.append(line).append('\n');
            if(clean.endsWith(delimiter)) { String sql=text.toString().stripTrailing(); ejecutar(c,sql.substring(0,sql.length()-delimiter.length()));text.setLength(0); }
        }
        assertTrue(text.toString().isBlank());
    }
}
