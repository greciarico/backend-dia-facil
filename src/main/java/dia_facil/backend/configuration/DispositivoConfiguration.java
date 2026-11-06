package dia_facil.backend.configuration;

import dia_facil.backend.service.dispositivos.*;
import jakarta.persistence.EntityManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods = false)
public class DispositivoConfiguration {
    @Bean @ConditionalOnProperty(name="diafacil.dispositivos.enabled", havingValue="true")
    public ApplicationRunner comprobarE3(TransicionCodigosDispositivo codigos, EntityManager em, AccesoDispositivoService service,
        org.springframework.transaction.PlatformTransactionManager transactions) {
        return args -> {
            // Both queries fail closed before the server becomes ready. No schema writes.
            // Keep the shared EntityManager's unwrapped Session open during JDBC metadata work.
            var verification = new org.springframework.transaction.support.TransactionTemplate(transactions);
            verification.setReadOnly(true);
            verification.executeWithoutResult(status -> EsquemaDispositivo.verificar(em));
            var a = codigos.auditar();
            if (a.legacy() != 0 || a.inesperados() != 0)
                throw new IllegalStateException("E3 deshabilitado: completa la auditoría/transición de códigos antes de activarlo.");
            service.habilitar();
        };
    }
    @Bean @ConditionalOnProperty(name="diafacil.dispositivos.codigos.transicion")
    public ApplicationRunner transicionCodigos(TransicionCodigosDispositivo codigos, Environment env) {
        return args -> {
            if (!"none".equals(env.getProperty("spring.main.web-application-type"))
                || !"none".equals(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                || env.getProperty("diafacil.datos-base.enabled", Boolean.class, true)
                || env.getProperty("diafacil.dispositivos.enabled", Boolean.class, false))
                throw new IllegalStateException("La transición requiere modo no-web, ddl-auto=none, datos base y E3 desactivados.");
            var mode = env.getProperty("diafacil.dispositivos.codigos.transicion");
            if (!"audit".equals(mode) && !"apply".equals(mode)) throw new IllegalStateException("Usa audit o apply.");
            var before = codigos.auditar();
            informar("Antes", before);
            before.exigirIntegridad();
            if ("apply".equals(mode)) {
                long changed = codigos.aplicar(); var after = codigos.auditar();
                System.out.printf("Transición: cambiados=%d%n", changed);
                informar("Después", after); after.exigirIntegridad();
                if (after.legacy() != 0) throw new IllegalStateException("Quedan códigos legacy; no activar E3.");
                System.out.println("Comprobación de códigos E3 superada; el arranque normal verificará también el esquema de credenciales.");
            }
        };
    }
    private static void informar(String momento, TransicionCodigosDispositivo.Auditoria audit) {
        System.out.printf("%s: total=%d actuales=%d legacy=%d inesperados=%d duplicados=%d unique_codigo=%s%n",
            momento, audit.total(), audit.actuales(), audit.legacy(), audit.inesperados(), audit.duplicados(), audit.codigoUnico());
    }
    @Configuration @EnableScheduling
    @ConditionalOnProperty(name="diafacil.dispositivos.enabled",havingValue="true")
    public static class Limpieza {
        private final AccesoDispositivoService service;
        public Limpieza(AccesoDispositivoService service) { this.service = service; }
        @Scheduled(fixedDelay=3600000,initialDelay=3600000) public void limpiar() { service.limpiarPreparaciones(); }
    }
}
