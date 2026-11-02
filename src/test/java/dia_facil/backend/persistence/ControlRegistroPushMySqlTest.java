package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.ControlRegistroPush;
import java.io.StringWriter;
import java.sql.*;
import java.util.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.*;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.exec.ScriptTargetOutputToWriter;
import org.hibernate.tool.schema.spi.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Only a fresh, dedicated diafacil_test_* schema missing the B1 control table. No Boot or real database. */
@EnabledIfEnvironmentVariable(named="DIAFACIL_TEST_MYSQL_URL", matches=".+")
class ControlRegistroPushMySqlTest {
    @Test void updateSoloCreaControlPreservaTokenYEsIdempotente() throws Exception {
        var url=System.getenv("DIAFACIL_TEST_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/;]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).matches(".*(databaseterm|createdatabaseifnotexist|sessionvariables|initsql).*"));
        var ds=new DriverManagerDataSource(url,System.getenv("DIAFACIL_TEST_MYSQL_USER"),System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try(var c=ds.getConnection()) {
            assertTrue(c.getCatalog().startsWith("diafacil_test_"));
            String before;
            try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE token_push")) {assertTrue(r.next());before=r.getString(2);}
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='control_registro_push'")) {assertTrue(r.next());assertEquals(0,r.getInt(1));}
            var settings=new HashMap<String,Object>();
            settings.put("hibernate.connection.datasource",ds);settings.put("hibernate.hbm2ddl.auto","none");
            settings.put("hibernate.hbm2ddl.halt_on_error",true);
            var registry=new StandardServiceRegistryBuilder().applySettings(settings).build();
            try {
                var metadata=new MetadataSources(registry).addAnnotatedClass(ControlRegistroPush.class).buildMetadata();
                var writer=new StringWriter();
                TargetDescriptor script=new TargetDescriptor() {
                    public EnumSet<TargetType> getTargetTypes(){return EnumSet.of(TargetType.SCRIPT);}
                    public ScriptTargetOutput getScriptTargetOutput(){return new ScriptTargetOutputToWriter(writer);}
                };
                var options=SchemaManagementToolCoordinator.buildExecutionOptions(settings,e->{throw e;});
                var migrator=registry.getService(SchemaManagementTool.class).getSchemaMigrator(settings);
                migrator.doMigration(metadata,options,ContributableMatcher.ALL,script);
                String preview=writer.toString().toLowerCase(Locale.ROOT);
                assertTrue(preview.contains("create table control_registro_push"));
                assertFalse(preview.contains("alter table"));assertFalse(preview.contains("drop "));
                assertEquals(1,preview.chars().filter(ch->ch==';').count());
                TargetDescriptor database=new TargetDescriptor() {
                    public EnumSet<TargetType> getTargetTypes(){return EnumSet.of(TargetType.DATABASE);}
                    public ScriptTargetOutput getScriptTargetOutput(){return null;}
                };
                migrator.doMigration(metadata,options,ContributableMatcher.ALL,database);
                writer.getBuffer().setLength(0);
                migrator.doMigration(metadata,options,ContributableMatcher.ALL,script);
                assertTrue(writer.toString().isBlank(),"Second update must not emit DDL");
                try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE token_push")) {assertTrue(r.next());assertEquals(before,r.getString(2));}
                try(var s=c.createStatement()) {s.executeUpdate("INSERT INTO control_registro_push(id_instalacion) VALUES('00000000-0000-4000-8000-000000000001')");}
                try(var s=c.createStatement();var r=s.executeQuery("SELECT version FROM control_registro_push")) {assertTrue(r.next());assertEquals(0,r.getLong(1));}
                try(var s=c.createStatement()) {assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO control_registro_push(id_instalacion) VALUES('00000000-0000-4000-8000-000000000001')"));}
                try(var s=c.createStatement()) {s.executeUpdate("DELETE FROM control_registro_push WHERE id_instalacion='00000000-0000-4000-8000-000000000001'");}
            } finally {StandardServiceRegistryBuilder.destroy(registry);}
        }
    }
}
