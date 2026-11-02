package dia_facil.backend.persistence;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.*;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.exec.ScriptTargetOutputToWriter;
import org.hibernate.tool.schema.spi.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: updates only an already prepared, exclusive diafacil_test_* schema. No Spring startup. */
@EnabledIfEnvironmentVariable(named="DIAFACIL_TEST_MYSQL_URL", matches=".+")
class PushSchemaUpdateMySqlTest {
    private final String url = System.getenv("DIAFACIL_TEST_MYSQL_URL");
    private DriverManagerDataSource ds;
    private Integer owner;

    @Test void updatePreservaTokensLegacyCreaRestriccionesYSegundoArranqueEsIdempotente() throws Exception {
        assertTrue(url.matches("jdbc:mysql://[^/;]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).matches(".*(databaseterm|createdatabaseifnotexist|sessionvariables|initsql).*"));
        ds = new DriverManagerDataSource(url, System.getenv("DIAFACIL_TEST_MYSQL_USER"), System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try (var c = ds.getConnection()) {
            assertTrue(c.getCatalog().startsWith("diafacil_test_"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM token_push"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='entrega_push'"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token_push' AND column_name='activo'"));
            try (var s = c.prepareStatement("INSERT INTO cuidador(nombre,email,estado) VALUES('Schema test',?,'ACTIVO')", Statement.RETURN_GENERATED_KEYS)) {
                s.setString(1, UUID.randomUUID()+"@example.invalid"); s.executeUpdate();
                try (var r=s.getGeneratedKeys()) { assertTrue(r.next()); owner=r.getInt(1); }
            }
            for (int i=0;i<2;i++) try (var s=c.prepareStatement("INSERT INTO token_push(expo_token,plataforma,id_cuidador) VALUES(?,'ANDROID',?)")) {
                s.setString(1,"ExpoPushToken[legacy_"+UUID.randomUUID().toString().replace("-","")+"]");s.setInt(2,owner);s.executeUpdate();
            }
        }
        try {
            String preview=preview();
            Files.writeString(Path.of("target","push-schema-update-preview.sql"),preview);
            assertTrue(preview.contains("create table") && preview.contains("entrega_push"));
            assertFalse(preview.toLowerCase(Locale.ROOT).contains("modify column"), "No unnecessary credential ALTER allowed");
            assertFalse(preview.toLowerCase(Locale.ROOT).contains("drop table"));
            for (String statement:preview.split(";")) if(!statement.isBlank()) {
                assertTrue(statement.contains("token_push") || statement.contains("entrega_push") || statement.contains("control_registro_push"),statement);
            }
            update();
            verifySchemaAndLegacyRows();
            assertTrue(preview().isBlank(),"A second update must not repeat ALTERs, indexes or FK creation");
            update();
            verifySchemaAndLegacyRows();
        } finally {
            if(owner!=null) try(var c=ds.getConnection();var s=c.prepareStatement("DELETE FROM cuidador WHERE id_cuidador=?")) {s.setInt(1,owner);s.executeUpdate();}
        }
    }
    private Map<String,Object> settings(String action) {
        var values=new HashMap<String,Object>();values.put("hibernate.connection.datasource",ds);
        values.put("hibernate.hbm2ddl.auto",action);values.put("hibernate.hbm2ddl.halt_on_error",true);
        values.put("hibernate.schema_update.unique_constraint_strategy","RECREATE_QUIETLY");return values;
    }
    private MetadataSources sources(StandardServiceRegistry registry) {
        var sources=new MetadataSources(registry);PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);return sources;
    }
    private String preview() {
        var settings=settings("none");var registry=new StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            var writer=new StringWriter();var metadata=sources(registry).buildMetadata();
            var descriptor=new TargetDescriptor() {
                public EnumSet<TargetType> getTargetTypes(){return EnumSet.of(TargetType.SCRIPT);}
                public ScriptTargetOutput getScriptTargetOutput(){return new ScriptTargetOutputToWriter(writer);}
            };
            registry.getService(SchemaManagementTool.class).getSchemaMigrator(settings).doMigration(metadata,
                SchemaManagementToolCoordinator.buildExecutionOptions(settings,e->{throw e;}),ContributableMatcher.ALL,descriptor);
            return writer.toString();
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
    private void update() {
        var registry=new StandardServiceRegistryBuilder().applySettings(settings("update")).build();
        try(SessionFactory ignored=sources(registry).buildMetadata().buildSessionFactory()) { assertTrue(ignored.isOpen()); }
        finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
    private void verifySchemaAndLegacyRows() throws Exception {
        try(var c=ds.getConnection()) {
            assertEquals(2,scalar(c,"SELECT COUNT(*) FROM token_push"));
            assertEquals(2,scalar(c,"SELECT COUNT(*) FROM token_push WHERE activo=0 AND generacion=1 AND id_instalacion IS NULL AND id_sesion IS NULL AND proyecto_eas IS NULL"));
            assertEquals(0,scalar(c,"SELECT COUNT(*) FROM entrega_push"));
            index(c,"token_push","uq_token_push",0,"expo_token");
            index(c,"token_push","uq_token_instalacion",0,"id_instalacion");
            index(c,"entrega_push","uq_entrega_destino",0,"id_notificacion_cuidador,id_token_push,generacion");
            index(c,"entrega_push","idx_entrega_pendiente",1,"estado,proxima_en");
            foreignKey(c,"fk_token_sesion","SET NULL");foreignKey(c,"fk_entrega_token","CASCADE");foreignKey(c,"fk_entrega_notificacion","CASCADE");
            assertEquals(3,scalar(c,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_type='binary(32)' AND ((table_name='credencial_dispositivo' AND column_name='token_hash') OR (table_name='credencial_sesion_cuidador' AND column_name IN ('access_hash','refresh_hash')))"));
        }
    }
    private static void index(Connection c,String table,String name,int nonUnique,String columns) throws Exception {
        try(var s=c.prepareStatement("SELECT NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=? GROUP BY NON_UNIQUE")) {
            s.setString(1,table);s.setString(2,name);try(var r=s.executeQuery()){assertTrue(r.next(),name);assertEquals(nonUnique,r.getInt(1));assertEquals(columns,r.getString(2));assertFalse(r.next());}
        }
    }
    private static void foreignKey(Connection c,String name,String rule) throws Exception {
        try(var s=c.prepareStatement("SELECT DELETE_RULE FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name=?")) {
            s.setString(1,name);try(var r=s.executeQuery()){assertTrue(r.next(),name);assertEquals(rule,r.getString(1));assertFalse(r.next());}
        }
    }
    private static long scalar(Connection c,String query) throws Exception {try(var s=c.createStatement();var r=s.executeQuery(query)){assertTrue(r.next());return r.getLong(1);}}
}
