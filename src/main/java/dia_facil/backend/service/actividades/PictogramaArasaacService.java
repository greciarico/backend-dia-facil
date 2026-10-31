package dia_facil.backend.service.actividades;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PictogramaArasaacService {
    private record Entrada(long vence, List<PictogramaArasaac> datos) {}
    private final LinkedHashMap<String, Entrada> cache = new LinkedHashMap<>();
    private final ArasaacClient client;
    private final Clock clock;
    @Autowired public PictogramaArasaacService(ArasaacClient client) { this(client, Clock.systemUTC()); }
    PictogramaArasaacService(ArasaacClient client, Clock clock) { this.client = client; this.clock = clock; }

    public PictogramaArasaac resolver(Integer id) {
        if (id == null || id <= 0) throw RechazoActividad.pictograma();
        synchronized (cache) {
            for (var entrada : cache.values()) if (entrada.vence() > clock.millis()) {
                for (var dato : entrada.datos()) if (id.equals(dato.idArasaac())) return dato;
            }
        }
        return client.detalle(id);
    }

    public List<PictogramaArasaac> buscar(String texto) {
        if (texto == null) return List.of();
        if (texto.length() > 120) throw new IllegalArgumentException();
        String query = texto.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (query.length() < 3) return List.of();
        synchronized (cache) {
            var entrada = cache.get(query);
            if (entrada != null && entrada.vence() > clock.millis()) return entrada.datos();
            cache.remove(query);
        }
        // No monitor held while contacting the external service.
        var datos = client.buscar(query);
        synchronized (cache) {
            cache.entrySet().removeIf(e -> e.getValue().vence() <= clock.millis());
            if (cache.size() >= 100) cache.remove(cache.keySet().iterator().next());
            cache.put(query, new Entrada(clock.millis() + 300_000, datos));
        }
        return datos;
    }
}
