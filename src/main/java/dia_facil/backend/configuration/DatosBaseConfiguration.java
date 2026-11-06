package dia_facil.backend.configuration;

import dia_facil.backend.persistence.repository.JpaDatosBaseStore;
import dia_facil.backend.service.datosbase.*;
import jakarta.persistence.EntityManager;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "diafacil.datos-base", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatosBaseConfiguration {
    @Bean
    public DatosBase datosBase(ObjectMapper mapper) {
        return LectorDatosBase.leer(mapper, new ClassPathResource("db/datos-base.json"));
    }
    @Bean
    public DatosBaseStore datosBaseStore(EntityManager em) { return new JpaDatosBaseStore(em); }
    @Bean
    public VerificadorEsquemaDatosBase verificadorEsquemaDatosBase(EntityManager em) { return new VerificadorEsquemaDatosBase(em); }
    @Bean
    public SincronizadorDatosBase sincronizadorDatosBase(DatosBaseStore store) { return new SincronizadorDatosBase(store); }
    @Bean
    public InicializadorDatosBase inicializadorDatosBase(DatosBase datos, SincronizadorDatosBase sincronizador,
            VerificadorEsquemaDatosBase esquema, PlatformTransactionManager manager) {
        return new InicializadorDatosBase(datos, sincronizador, esquema, manager);
    }
    @Bean
    public ApplicationRunner inicializarDatosBase(InicializadorDatosBase inicializador) {
        return args -> {
            var resultado = inicializador.inicializar(); // Logging occurs only after a successful commit.
            var log = LoggerFactory.getLogger(DatosBaseConfiguration.class);
            log.info("Datos base: {} creados, {} existentes, {} conflictos conservados.", resultado.creados(), resultado.existentes(), resultado.conflictos().size());
            resultado.conflictos().forEach(clave -> log.warn("Datos base: diferencia conservada en {}.", clave));
        };
    }
}
