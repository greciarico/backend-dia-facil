package dia_facil.backend.configuration;

import dia_facil.backend.service.auth.SesionCuidadorService;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling
@ConditionalOnProperty(prefix = "diafacil.auth", name = "enabled", havingValue = "true")
public class LimpiezaSesiones {
    private final SesionCuidadorService sesiones;
    public LimpiezaSesiones(SesionCuidadorService sesiones) { this.sesiones = sesiones; }
    @Scheduled(fixedDelay = 86400000, initialDelay = 86400000)
    public void limpiar() { sesiones.limpiarFinalizadas(); }
}
