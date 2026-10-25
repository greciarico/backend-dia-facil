package dia_facil.backend.service.dispositivos;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Single-instance limiter. No codes, headers or device credentials are retained. */
public final class LimiteVinculacion {
    private record Ventana(long minuto, long hora, int minutos, int horas) {}
    private final Map<String, Ventana> origenes = new HashMap<>();
    private final Clock clock;
    private final int porMinuto, porHora, global;
    private long minutoGlobal = -1; private int total;
    public LimiteVinculacion(Clock clock, int porMinuto, int porHora, int global) {
        if (porMinuto < 1 || porHora < 1 || global < 1) throw new IllegalArgumentException("Límites positivos requeridos");
        this.clock = clock; this.porMinuto = porMinuto; this.porHora = porHora; this.global = global;
    }
    public synchronized int espera(String origen) {
        long seconds = clock.instant().getEpochSecond(), minute = seconds / 60, hour = seconds / 3600;
        if (minute != minutoGlobal) { minutoGlobal = minute; total = 0; origenes.values().removeIf(v -> v.hora < hour); }
        if (total >= global) return (int) (60 - seconds % 60);
        var old = origenes.get(origen);
        if (old == null && origenes.size() >= 10_000) return 60;
        int minutes = old != null && old.minuto == minute ? old.minutos : 0;
        int hours = old != null && old.hora == hour ? old.horas : 0;
        if (hours >= porHora) return (int) (3600 - seconds % 3600);
        if (minutes >= porMinuto) return (int) (60 - seconds % 60);
        origenes.put(origen, new Ventana(minute, hour, minutes + 1, hours + 1)); total++; return 0;
    }
}
