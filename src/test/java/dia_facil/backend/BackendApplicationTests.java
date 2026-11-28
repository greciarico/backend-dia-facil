package dia_facil.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_MYSQL_URL", matches = "(?s).*\\S.*")
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "diafacil.datos-base.enabled=false",
    "diafacil.cloudinary.enabled=false"
})
class BackendApplicationTests {

    @DynamicPropertySource
    static void mysqlDePruebas(DynamicPropertyRegistry registry) {
        String url = System.getenv("DIAFACIL_TEST_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://[A-Za-z0-9.-]+(?::[0-9]+)?/diafacil_test_[A-Za-z0-9_]+"))
            throw new IllegalStateException("Usa una URL MySQL de pruebas con esquema diafacil_test_* y sin parámetros.");
        String usuario = System.getenv("DIAFACIL_TEST_MYSQL_USER");
        String clave = System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD");
        if (usuario == null || usuario.isBlank() || clave == null)
            throw new IllegalStateException("Configura el usuario y la contraseña de MySQL de pruebas.");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> usuario);
        registry.add("spring.datasource.password", () -> clave);
    }

    @Test
    void contextLoads() {
    }
}
