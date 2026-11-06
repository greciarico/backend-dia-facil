package dia_facil.backend.configuration;

import java.util.Locale;
import java.util.Set;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Preserve development update while refusing destructive schema recreation. */
public class SchemaSafetyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        for (String key : new String[] { "spring.jpa.hibernate.ddl-auto",
                "spring.jpa.properties.hibernate.hbm2ddl.auto",
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action" }) {
            String value = env.getProperty(key, "").trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty() && !Set.of("none", "validate", "update").contains(value)) {
                throw new IllegalStateException("DDL destructivo no permitido: " + key
                    + " debe ser none, validate o update. No se inicio JPA.");
            }
        }
    }
}
