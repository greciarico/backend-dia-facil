package dia_facil.backend.configuration;

import dia_facil.backend.service.datosbase.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatosBaseConfigurationTest {
    @Test void deshabilitacionExplicitaNoCreaRunnerNiExigeJpaOJson() {
        new ApplicationContextRunner().withUserConfiguration(DatosBaseConfiguration.class)
            .withPropertyValues("diafacil.datos-base.enabled=false").run(c -> {
                assertNull(c.getStartupFailure()); assertTrue(c.getBeansOfType(ApplicationRunner.class).isEmpty());
                assertTrue(c.getBeansOfType(DatosBase.class).isEmpty());
            });
    }
    @Test void habilitadoPorDefectoEnCualquierPerfilYNoAccedeABDAlConstruirBeans() {
        var em = mock(EntityManager.class); var tx = mock(PlatformTransactionManager.class);
        new ApplicationContextRunner().withUserConfiguration(DatosBaseConfiguration.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(EntityManager.class, () -> em).withBean(PlatformTransactionManager.class, () -> tx)
            .withPropertyValues("spring.profiles.active=production").run(c -> {
                assertNull(c.getStartupFailure()); assertEquals(1, c.getBeansOfType(ApplicationRunner.class).size());
                assertNotNull(c.getBean(InicializadorDatosBase.class)); assertEquals(52, c.getBean(DatosBase.class).iconos().size());
                verifyNoInteractions(em, tx); // Actual writes are deferred to the runner, after JPA startup.
            });
    }
}
