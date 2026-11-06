package dia_facil.backend.service.perfiles;

import java.time.Duration;
import java.time.LocalDateTime;

public final class PoliticaVinculacion {
    public static final int RENOVACION_EN_SEGUNDOS = 60;
    private PoliticaVinculacion() {}
    public static long espera(LocalDateTime ultimaRenovacion, LocalDateTime ahora) {
        if (ultimaRenovacion == null) return 0;
        var restante = Duration.between(ahora, ultimaRenovacion.plusSeconds(RENOVACION_EN_SEGUNDOS));
        if (restante.isNegative() || restante.isZero()) return 0;
        return restante.getSeconds() + (restante.getNano() > 0 ? 1 : 0);
    }
}
