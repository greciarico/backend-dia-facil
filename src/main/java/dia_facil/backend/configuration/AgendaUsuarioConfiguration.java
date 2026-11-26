package dia_facil.backend.configuration;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
public class AgendaUsuarioConfiguration {
    @Bean public Clock agendaUsuarioClock(Environment env) {
        return Clock.system(ZoneId.of(env.getProperty("diafacil.agenda.zone","America/Lima")));
    }
}
