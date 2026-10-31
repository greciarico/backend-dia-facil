package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.repository.IconoActividadRepository;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IconoActividadService {
    public record Icono(String codigo, String nombre) {}
    private final IconoActividadRepository repository;
    public IconoActividadService(IconoActividadRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<Icono> consultar(String nombre) {
        if (nombre != null && nombre.length() > 80) throw new IllegalArgumentException();
        String query = normalizar(nombre == null ? "" : nombre);
        if (nombre != null && !query.isEmpty() && query.length() < 3) return List.of();
        Map<String, Icono> catalogo = new TreeMap<>();
        Map<String, Integer> relevancia = new HashMap<>();
        for (var fila : repository.consultarCatalogo()) {
            catalogo.putIfAbsent(fila.getCodigo(), new Icono(fila.getCodigo(), fila.getNombre()));
            if (!query.isEmpty() && fila.getPalabra() != null) {
                int score = puntuar(query, normalizar(fila.getPalabra()));
                if (score > 0) relevancia.merge(fila.getCodigo(), score, Math::max);
            }
        }
        if (query.isEmpty()) return List.copyOf(catalogo.values());
        return relevancia.keySet().stream()
            .sorted(Comparator.<String>comparingInt(relevancia::get).reversed().thenComparing(Comparator.naturalOrder()))
            .limit(3).map(catalogo::get).toList();
    }

    private static int puntuar(String query, String palabra) {
        if (query.equals(palabra)) return 3;
        if (Arrays.asList(query.split(" ")).contains(palabra)) return 2;
        return Arrays.stream(query.split(" ")).anyMatch(token -> token.length() >= 2 && palabra.startsWith(token)) ? 1 : 0;
    }

    private static String normalizar(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
