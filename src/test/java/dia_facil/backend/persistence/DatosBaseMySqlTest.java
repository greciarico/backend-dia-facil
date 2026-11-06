package dia_facil.backend.persistence;

import dia_facil.backend.persistence.repository.JpaDatosBaseStore;
import dia_facil.backend.service.datosbase.*;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** EMPTY, dedicated disposable database only; no development credentials or shared tables. */
@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_DATOS_BASE_MYSQL_URL", matches = ".+")
class DatosBaseMySqlTest {
    @Test void updateNuevoIdempotenciaRollbackConcurrenciaYTransicionSinCorrecciones() throws Exception {
        String url = System.getenv("DIAFACIL_TEST_DATOS_BASE_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_datos_base_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        var ds = new DriverManagerDataSource(url, System.getenv("DIAFACIL_TEST_DATOS_BASE_MYSQL_USER"), System.getenv("DIAFACIL_TEST_DATOS_BASE_MYSQL_PASSWORD"));
        try (var c = ds.getConnection()) {
            assertTrue(c.getCatalog().startsWith("diafacil_test_datos_base_"));
            assertEquals(0, numero(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"));
            assertEquals(0, numero(c, "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE()"));
            // After the empty-database gate, this test owns all objects it creates.
            var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource", ds)
                .applySetting("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD")
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .applySetting("hibernate.schema_update.unique_constraint_strategy", "RECREATE_QUIETLY").build();
            try {
                var sources = new MetadataSources(registry); PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
                try (var factory = sources.buildMetadata().buildSessionFactory()) {
                    var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
                    var manager = new JpaTransactionManager(factory); var tx = new TransactionTemplate(manager);
                    var datos = LectorDatosBase.leer(JsonMapper.builder().build(), new ClassPathResource("db/datos-base.json"));
                    var store = new JpaDatosBaseStore(em); var esquema = new VerificadorEsquemaDatosBase(em);
                    var sync = new SincronizadorDatosBase(store); var inicializador = new InicializadorDatosBase(datos, sync, esquema, manager);
                    assertEquals(97, inicializador.inicializar().creados());
                    assertEquals(0, inicializador.inicializar().creados());
                    assertEquals(52, numero(c, "SELECT COUNT(*) FROM icono_actividad"));
                    assertEquals(5, numero(c, "SELECT COUNT(*) FROM plantilla_paso"));
                    assertEquals(0, numero(c, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='plantilla_paso' AND INDEX_NAME='idx_plantilla_paso_orden'"));

                    // Real user entities are independent of the system template.
                    tx.executeWithoutResult(status -> {
                        em.createNativeQuery("INSERT INTO cuidador(nombre,email) VALUES('Cuenta de prueba','datos-base@example.invalid')").executeUpdate();
                        int cuidador = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()", Long.class).getSingleResult()).intValue();
                        em.createNativeQuery("INSERT INTO perfil_usuario(id_cuidador,nombre,codigo_vinculacion) VALUES(:id,'Persona de prueba','DF-987654')").setParameter("id", cuidador).executeUpdate();
                        int perfil = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()", Long.class).getSingleResult()).intValue();
                        em.createNativeQuery("INSERT INTO actividad(id_perfil_usuario,nombre,hora) VALUES(:id,'Rutina personalizada','08:00:00')").setParameter("id", perfil).executeUpdate();
                        int actividad = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()", Long.class).getSingleResult()).intValue();
                        em.createNativeQuery("INSERT INTO paso_actividad(id_actividad,orden,descripcion) VALUES(:id,1,'Paso personalizado')").setParameter("id", actividad).executeUpdate();
                        em.createNativeQuery("INSERT INTO ejecucion_actividad(id_actividad,fecha) VALUES(:id,'2026-01-01')").setParameter("id", actividad).executeUpdate();
                    });
                    var usuarioAntes = usuario(c);
                    ejecutar(c, "UPDATE plantilla_paso SET descripcion='Cambio revisado' WHERE orden=1");
                    assertFalse(inicializador.inicializar().conflictos().isEmpty());
                    assertEquals(usuarioAntes, usuario(c));
                    ejecutar(c, "UPDATE plantilla_paso SET descripcion='Lávate las manos' WHERE orden=1");

                    // A real write followed by an unknown failure must roll back completely.
                    var ampliados = new ArrayList<>(datos.iconos()); ampliados.add(new DatosBase.Icono("prueba-rollback", "Prueba", List.of()));
                    var falla = spy(sync);
                    doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("Fallo simulado"); }).when(falla).sincronizar(any());
                    assertThrows(IllegalStateException.class, () -> new InicializadorDatosBase(new DatosBase(ampliados, datos.pictogramas(), datos.plantillas()), falla, esquema, manager).inicializar());
                    assertEquals(0, numero(c, "SELECT COUNT(*) FROM icono_actividad WHERE codigo='prueba-rollback'"));

                    // Force both transactions to observe the same missing position.
                    ejecutar(c, "DELETE FROM plantilla_paso WHERE orden=2");
                    var concurrenteStore = spy(store); var barrera = new CountDownLatch(2);
                    doAnswer(i -> { barrera.countDown(); assertTrue(barrera.await(15, TimeUnit.SECONDS)); return i.callRealMethod(); })
                        .when(concurrenteStore).crearPaso(anyInt(), eq(2), anyString(), anyInt());
                    var concurrente = new InicializadorDatosBase(datos, new SincronizadorDatosBase(concurrenteStore), esquema, manager);
                    var pool = Executors.newFixedThreadPool(2);
                    try {
                        var a = pool.submit(concurrente::inicializar); var b = pool.submit(concurrente::inicializar);
                        assertEquals(1, a.get(40, TimeUnit.SECONDS).creados() + b.get(40, TimeUnit.SECONDS).creados());
                    } finally { pool.shutdownNow(); }
                    assertEquals(5, numero(c, "SELECT COUNT(*) FROM plantilla_paso"));
                    assertEquals(0, numero(c, "SELECT COUNT(*) FROM (SELECT orden FROM plantilla_paso GROUP BY id_plantilla_actividad,orden HAVING COUNT(*)>1) d"));
                    assertThrows(SQLException.class, () -> ejecutar(c, "INSERT INTO plantilla_paso(id_plantilla_actividad,orden,descripcion) SELECT id_plantilla_actividad,1,'Duplicado' FROM plantilla_actividad WHERE nombre='Desayunar'"));
                    ejecutar(c, "INSERT INTO plantilla_actividad(nombre) VALUES('Otra plantilla de prueba')");
                    ejecutar(c, "INSERT INTO plantilla_paso(id_plantilla_actividad,orden,descripcion) SELECT id_plantilla_actividad,1,'Otro' FROM plantilla_actividad WHERE nombre='Otra plantilla de prueba'");

                    // Legacy schema fixture: the manual script must refuse duplicates unchanged.
                    ejecutar(c, "ALTER TABLE plantilla_paso ADD INDEX idx_plantilla_paso_orden(id_plantilla_actividad,orden)");
                    ejecutar(c, "ALTER TABLE plantilla_paso DROP INDEX uq_plantilla_paso_orden");
                    ejecutar(c, "INSERT INTO plantilla_paso(id_plantilla_actividad,orden,descripcion) SELECT id_plantilla_actividad,1,'Duplicado de prueba' FROM plantilla_actividad WHERE nombre='Desayunar'");
                    long filas = numero(c, "SELECT COUNT(*) FROM plantilla_paso");
                    assertThrows(SQLException.class, () -> script(c));
                    assertEquals(filas, numero(c, "SELECT COUNT(*) FROM plantilla_paso"));
                    assertThrows(IllegalStateException.class, inicializador::inicializar);
                    assertEquals(usuarioAntes, usuario(c));
                    // Explicit test-fixture correction only; the migration does not perform it.
                    ejecutar(c, "DELETE FROM plantilla_paso WHERE descripcion='Duplicado de prueba'");
                    script(c); script(c); // Correct schema and rerun are both supported.
                    assertEquals(0, inicializador.inicializar().creados());
                    assertEquals(usuarioAntes, usuario(c));
                }
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
                ejecutar(c, "DROP PROCEDURE IF EXISTS df_unique_plantilla_paso_orden");
                for (var entity : PersistenceModelTest.ENTITIES.reversed()) {
                    ejecutar(c, "DROP TABLE IF EXISTS " + entity.getAnnotation(jakarta.persistence.Table.class).name());
                }
            }
        }
    }
    private static long numero(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement(); var rs = statement.executeQuery(sql)) { assertTrue(rs.next()); return rs.getLong(1); }
    }
    private static void ejecutar(Connection c, String sql) throws SQLException { try (var statement = c.createStatement()) { statement.execute(sql); } }
    private static Map<String, List<String>> usuario(Connection c) throws SQLException {
        var resultado = new LinkedHashMap<String, List<String>>();
        for (String tabla : List.of("cuidador", "perfil_usuario", "actividad", "paso_actividad", "ejecucion_actividad", "sesion_cuidador", "credencial_sesion_cuidador")) {
            var filas = new ArrayList<String>();
            try (var statement = c.createStatement(); var rs = statement.executeQuery("SELECT * FROM " + tabla + " ORDER BY 1")) {
                while (rs.next()) { var fila = new Object[rs.getMetaData().getColumnCount()]; for (int i = 0; i < fila.length; i++) fila[i] = rs.getObject(i + 1); filas.add(Arrays.deepToString(fila)); }
            }
            resultado.put(tabla, filas);
        }
        return resultado;
    }
    private static void script(Connection c) throws Exception {
        var texto = new StringBuilder(); String delimitador = ";";
        for (String linea : Files.readAllLines(Path.of("docs/sql/add-plantilla-paso-orden-unique.sql"))) {
            String limpia = linea.strip(); if (limpia.isEmpty() || limpia.startsWith("--")) continue;
            if (limpia.startsWith("DELIMITER ")) { delimitador = limpia.substring(10); continue; }
            texto.append(linea).append('\n');
            if (limpia.endsWith(delimitador)) {
                String sql = texto.toString().stripTrailing(); ejecutar(c, sql.substring(0, sql.length() - delimitador.length())); texto.setLength(0);
            }
        }
        assertTrue(texto.toString().isBlank());
    }
}
