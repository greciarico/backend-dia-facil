package dia_facil.backend.service.push;

import dia_facil.backend.configuration.SchemaSafetyEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class SchemaSafetyTest {
    private static final String[] KEYS = { "spring.jpa.hibernate.ddl-auto",
        "spring.jpa.properties.hibernate.hbm2ddl.auto",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action" };

    @Test void rechazaDdlDestructivoAntesDeJpa() {
        var guard = new SchemaSafetyEnvironmentPostProcessor();
        for (String key : KEYS) for (String value : new String[] { "create", "create-drop", "drop", "drop-and-create", "create-only", "unknown" }) {
            assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(
                new MockEnvironment().withProperty(key, value), null), key + "=" + value);
        }
    }

    @Test void permiteUpdateSinCambiarLaConfiguracion() {
        var guard = new SchemaSafetyEnvironmentPostProcessor();
        for (String key : KEYS) for (String value : new String[] { "none", "validate", "update", " UPDATE ", "" }) {
            var environment = new MockEnvironment().withProperty(key, value);
            assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
            assertEquals(value, environment.getProperty(key));
        }
    }

    @Test void unOverrideDestructivoNoSeOcultaTrasUpdate() {
        assertThrows(IllegalStateException.class, () -> new SchemaSafetyEnvironmentPostProcessor()
            .postProcessEnvironment(new MockEnvironment().withProperty(KEYS[0], "update")
                .withProperty(KEYS[1], "create-drop"), null));
    }
}
