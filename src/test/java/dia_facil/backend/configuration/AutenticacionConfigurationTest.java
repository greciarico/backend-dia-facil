package dia_facil.backend.configuration;

import dia_facil.backend.persistence.entity.CodigoAcceso;
import dia_facil.backend.persistence.repository.CodigoAccesoRepository;
import dia_facil.backend.persistence.repository.CuidadorRepository;
import dia_facil.backend.service.auth.AutenticacionCuidadorService;
import dia_facil.backend.service.auth.AutenticacionCuidadorTransaccionalService;
import dia_facil.backend.service.auth.CriptografiaAutenticacion;
import dia_facil.backend.service.auth.EnvioCodigoAcceso;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutenticacionConfigurationTest {
    private static final String OTP = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String REGISTRO = Base64.getEncoder().encodeToString("r".repeat(32).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String EMAIL = "test@example.com";

    private ApplicationContextRunner base() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TransactionAutoConfiguration.class))
            .withUserConfiguration(AutenticacionConfiguration.class);
    }

    private ApplicationContextRunner activado() {
        return base().withPropertyValues("diafacil.auth.enabled=true",
            "diafacil.auth.otp-secret=" + OTP, "diafacil.auth.registration-secret=" + REGISTRO);
    }

    private ApplicationContextRunner dependencias(ApplicationContextRunner runner, Manager manager,
            CuidadorRepository cuidadores, CodigoAccesoRepository codigos, EnvioCodigoAcceso envio) {
        return runner.withBean(CuidadorRepository.class, () -> cuidadores)
            .withBean(CodigoAccesoRepository.class, () -> codigos)
            .withBean(dia_facil.backend.persistence.repository.SesionCuidadorRepository.class,
                () -> mock(dia_facil.backend.persistence.repository.SesionCuidadorRepository.class))
            .withBean(dia_facil.backend.persistence.repository.CredencialSesionCuidadorRepository.class,
                () -> mock(dia_facil.backend.persistence.repository.CredencialSesionCuidadorRepository.class))
            .withBean(PlatformTransactionManager.class, () -> manager)
            .withBean(EnvioCodigoAcceso.class, () -> envio);
    }

    private ApplicationContextRunner completo() {
        return dependencias(activado(), new Manager(), mock(CuidadorRepository.class),
            mock(CodigoAccesoRepository.class), mock(EnvioCodigoAcceso.class));
    }

    @Test void desactivadoNoRequiereSecretosNiColaboradores() {
        for (var runner : List.of(base(), base().withPropertyValues("diafacil.auth.enabled=false"))) {
            runner.run(context -> {
                assertNull(context.getStartupFailure());
                assertTrue(context.getBeansOfType(AutenticacionCuidadorService.class).isEmpty());
                assertTrue(context.getBeansOfType(AutenticacionCuidadorTransaccionalService.class).isEmpty());
                assertTrue(context.getBeansOfType(CriptografiaAutenticacion.class).isEmpty());
                assertTrue(context.getBeansOfType(Clock.class).isEmpty());
                assertTrue(context.getBeansOfType(SecureRandom.class).isEmpty());
            });
        }
    }

    @Test void creaBeansUnicosYLaFachadaRecibeElProxyTransaccional() {
        completo().run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(AutenticacionCuidadorService.class).size());
            assertEquals(1, context.getBeansOfType(AutenticacionCuidadorTransaccionalService.class).size());
            assertEquals(1, context.getBeansOfType(CriptografiaAutenticacion.class).size());
            assertEquals(1, context.getBeansOfType(SecureRandom.class).size());
            var clock = context.getBean("autenticacionClock", Clock.class);
            assertEquals(ZoneId.of("America/Lima"), clock.getZone());
            var worker = context.getBean(AutenticacionCuidadorTransaccionalService.class);
            assertTrue(AopUtils.isAopProxy(worker));
            var fachada = context.getBean(AutenticacionCuidadorService.class);
            assertSame(worker, ReflectionTestUtils.getField(fachada, "transacciones"));
            assertSame(context.getBean(CriptografiaAutenticacion.class), ReflectionTestUtils.getField(fachada, "criptografia"));
        });
    }

    @Test void zonaConfigurableYSinModificarRelojesAjenos() {
        completo().withPropertyValues("diafacil.auth.zone=UTC")
            .withBean("otroClock", Clock.class, Clock::systemUTC)
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(ZoneId.of("UTC"), context.getBean("autenticacionClock", Clock.class).getZone());
                assertSame(context.getBean("autenticacionClock"),
                    ReflectionTestUtils.getField(context.getBean(CriptografiaAutenticacion.class), "clock"));
            });
    }

    @ParameterizedTest @ValueSource(strings = {"", "NO_ES_BASE64_SECRETO", "YWJj"})
    void rechazaCadaSecretoInvalidoSinDivulgarlo(String valor) {
        for (var nombre : List.of("diafacil.auth.otp-secret", "diafacil.auth.registration-secret")) {
            completo().withPropertyValues(nombre + "=" + valor).run(context -> {
                var fallo = context.getStartupFailure();
                assertNotNull(fallo);
                for (var causa = fallo; causa != null; causa = causa.getCause()) {
                    var mensaje = causa.getMessage();
                    if (!valor.isEmpty() && mensaje != null) assertFalse(mensaje.contains(valor));
                }
                var raiz = fallo;
                while (raiz.getCause() != null) raiz = raiz.getCause();
                assertTrue(raiz.getMessage().contains(nombre));
            });
        }
    }

    @Test void rechazaSecretosAusentesOIgualesYZonaInvalida() {
        for (var nombre : List.of("diafacil.auth.otp-secret", "diafacil.auth.registration-secret")) {
            var otro = nombre.endsWith("otp-secret") ? "diafacil.auth.registration-secret" : "diafacil.auth.otp-secret";
            var runner = base().withPropertyValues("diafacil.auth.enabled=true", otro + "=" + OTP);
            dependencias(runner, new Manager(), mock(CuidadorRepository.class), mock(CodigoAccesoRepository.class),
                mock(EnvioCodigoAcceso.class)).run(context -> assertNotNull(context.getStartupFailure()));
        }
        completo().withPropertyValues("diafacil.auth.registration-secret=" + OTP)
            .run(context -> assertNotNull(context.getStartupFailure()));
        completo().withPropertyValues("diafacil.auth.zone=zona-invalida")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test void moduloActivadoExigeRepositoriosGestorYEnvio() {
        activado().withBean(CuidadorRepository.class, () -> mock(CuidadorRepository.class))
            .withBean(CodigoAccesoRepository.class, () -> mock(CodigoAccesoRepository.class))
            .withBean(PlatformTransactionManager.class, Manager::new)
            .run(context -> assertNotNull(context.getStartupFailure()));
        activado().withBean(CuidadorRepository.class, () -> mock(CuidadorRepository.class))
            .withBean(CodigoAccesoRepository.class, () -> mock(CodigoAccesoRepository.class))
            .withBean(EnvioCodigoAcceso.class, () -> mock(EnvioCodigoAcceso.class))
            .run(context -> assertNotNull(context.getStartupFailure()));
        activado().withBean(PlatformTransactionManager.class, Manager::new)
            .withBean(EnvioCodigoAcceso.class, () -> mock(EnvioCodigoAcceso.class))
            .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test void fachadaEnsambladaConfirmaIntentosYRevierteFallosTecnicos() {
        var manager = new Manager();
        var codigos = mock(CodigoAccesoRepository.class);
        dependencias(activado(), manager, mock(CuidadorRepository.class), codigos, mock(EnvioCodigoAcceso.class))
            .run(context -> {
                assertNull(context.getStartupFailure());
                var crypto = context.getBean(CriptografiaAutenticacion.class);
                var registro = CodigoAcceso.emitir(EMAIL, crypto.hashCodigo(EMAIL, "000123"), crypto.ahora().plusMinutes(10));
                when(codigos.findFirstByEmailOrderByFechaCreacionDescIdCodigoAccesoDesc(EMAIL)).thenReturn(Optional.of(registro));
                var facade = context.getBean(AutenticacionCuidadorService.class);
                assertEquals(AutenticacionCuidadorService.Motivo.CODIGO_INCORRECTO,
                    facade.verificarCodigo(EMAIL, "999999").motivo());
                assertEquals(1, registro.getIntentos());
                assertEquals(List.of("begin", "commit"), manager.events);
                when(codigos.save(any())).thenThrow(new IllegalStateException("Persistencia"));
                assertThrows(IllegalStateException.class, () -> facade.verificarCodigo(EMAIL, "999999"));
                assertEquals(List.of("begin", "commit", "begin", "rollback"), manager.events);
            });
    }

    @Test void fachadaEnsambladaEnviaDespuesDelCommit() {
        var manager = new Manager();
        var codigos = mock(CodigoAccesoRepository.class);
        dependencias(activado(), manager, mock(CuidadorRepository.class), codigos,
            (email, codigo) -> manager.events.add("send")).run(context -> {
                assertNull(context.getStartupFailure());
                context.getBean(AutenticacionCuidadorService.class).solicitarCodigo(EMAIL);
                assertEquals(List.of("begin", "commit", "send"), manager.events);
                verify(codigos).save(any());
            });
    }

    static class Manager extends AbstractPlatformTransactionManager {
        final List<String> events = new ArrayList<>();
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) {
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRED, definition.getPropagationBehavior());
            assertEquals(TransactionDefinition.ISOLATION_DEFAULT, definition.getIsolationLevel());
            events.add("begin");
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
