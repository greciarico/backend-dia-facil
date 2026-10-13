package dia_facil.backend.configuration;

import dia_facil.backend.persistence.repository.CodigoAccesoRepository;
import dia_facil.backend.persistence.repository.CuidadorRepository;
import dia_facil.backend.service.auth.AutenticacionCuidadorService;
import dia_facil.backend.service.auth.AutenticacionCuidadorTransaccionalService;
import dia_facil.backend.service.auth.CriptografiaAutenticacion;
import dia_facil.backend.service.auth.EnvioCodigoAcceso;
import dia_facil.backend.service.auth.SesionCuidadorService;
import dia_facil.backend.service.auth.CriptografiaSesion;
import dia_facil.backend.persistence.repository.SesionCuidadorRepository;
import dia_facil.backend.persistence.repository.CredencialSesionCuidadorRepository;
import java.time.Duration;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "diafacil.auth", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AutenticacionConfiguration {

    @Bean
    public Clock autenticacionClock(Environment environment) {
        try {
            return Clock.system(ZoneId.of(environment.getProperty("diafacil.auth.zone", "America/Lima")));
        } catch (DateTimeException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("diafacil.auth.zone debe identificar una zona horaria valida");
        }
    }

    @Bean
    public SecureRandom autenticacionSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    public CriptografiaAutenticacion criptografiaAutenticacion(Environment environment,
            @Qualifier("autenticacionClock") Clock clock,
            @Qualifier("autenticacionSecureRandom") SecureRandom random) {
        return new CriptografiaAutenticacion(clock, random,
            secreto(environment, "diafacil.auth.otp-secret"),
            secreto(environment, "diafacil.auth.registration-secret"));
    }

    @Bean
    public AutenticacionCuidadorTransaccionalService autenticacionCuidadorTransaccionalService(
            CuidadorRepository cuidadores, CodigoAccesoRepository codigos,
            CriptografiaAutenticacion criptografia, PlatformTransactionManager transactionManager, SesionCuidadorService sesiones) {
        // Requiring the existing manager prevents an enabled module without transaction infrastructure.
        return new AutenticacionCuidadorTransaccionalService(cuidadores, codigos, criptografia, sesiones);
    }

    @Bean
    public SesionCuidadorService sesionCuidadorService(SesionCuidadorRepository sesiones,
            CredencialSesionCuidadorRepository credenciales, CuidadorRepository cuidadores,
            @Qualifier("autenticacionClock") Clock clock, @Qualifier("autenticacionSecureRandom") SecureRandom random, Environment env) {
        return new SesionCuidadorService(sesiones, credenciales, cuidadores, clock, new CriptografiaSesion(random),
            duracion(env, "diafacil.auth.session.access", "PT15M"), duracion(env, "diafacil.auth.session.idle", "P30D"),
            duracion(env, "diafacil.auth.session.absolute", "P90D"));
    }
    private static Duration duracion(Environment env, String key, String fallback) {
        try {
            var duration = Duration.parse(env.getProperty(key, fallback));
            if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException();
            return duration;
        } catch (RuntimeException ex) { throw new IllegalArgumentException(key + " requiere duración ISO positiva"); }
    }

    @Bean
    public AutenticacionCuidadorService autenticacionCuidadorService(
            AutenticacionCuidadorTransaccionalService transacciones,
            CriptografiaAutenticacion criptografia, EnvioCodigoAcceso envio) {
        return new AutenticacionCuidadorService(transacciones, criptografia, envio);
    }

    private static byte[] secreto(Environment environment, String propiedad) {
        // Never include the supplied value or a decoder/property-resolution cause in diagnostics.
        try {
            var valor = environment.getProperty(propiedad);
            if (valor == null || valor.isBlank()) throw new IllegalArgumentException();
            var bytes = Base64.getDecoder().decode(valor);
            if (bytes.length < 32) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(propiedad + " requiere Base64 valido de al menos 32 bytes");
        }
    }
}
