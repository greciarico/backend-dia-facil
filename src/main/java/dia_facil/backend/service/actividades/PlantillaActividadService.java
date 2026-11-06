package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.repository.PlantillaActividadRepository;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlantillaActividadService {
    public record Pictograma(Integer idPictograma, String nombre, String fuente, Integer idArasaac, String urlImagen) {
        public Pictograma(Integer id, String nombre) { this(id, nombre, null, null, null); }
        public static Pictograma de(Integer id, String nombre, Integer arasaac) {
            return new Pictograma(id, nombre, arasaac == null ? null : "ARASAAC", arasaac, PictogramaArasaac.imagen(arasaac));
        }
    }
    public record Paso(Integer orden, String descripcion, Pictograma pictograma) {}
    public record Plantilla(Integer idPlantillaActividad, String nombre, List<Paso> pasos) {}
    private record Coincidencia(Integer id, String nombre, int prioridad) {}
    private final PlantillaActividadRepository repository;
    public PlantillaActividadService(PlantillaActividadRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public Plantilla sugerir(String nombre) {
        if (nombre != null && nombre.length() > 80) throw new IllegalArgumentException();
        String query = normalizar(nombre == null ? "" : nombre);
        if (query.length() < 3) return null;
        Map<Integer, Coincidencia> encontradas = new HashMap<>();
        for (var candidata : repository.candidatas()) {
            String titulo = normalizar(candidata.getNombre());
            String palabra = candidata.getPalabra() == null ? "" : normalizar(candidata.getPalabra());
            int prioridad = query.equals(titulo) ? 3 : !palabra.isEmpty() && query.equals(palabra) ? 2
                : contiene(query, titulo) || contiene(query, palabra) ? 1 : 0;
            if (prioridad > 0) encontradas.merge(candidata.getId(), new Coincidencia(candidata.getId(), candidata.getNombre(), prioridad),
                (a, b) -> a.prioridad() >= b.prioridad() ? a : b);
        }
        var seleccionada = encontradas.values().stream().sorted(Comparator.comparingInt(Coincidencia::prioridad).reversed()
            .thenComparing(c -> normalizar(c.nombre())).thenComparing(Coincidencia::id)).findFirst();
        if (seleccionada.isEmpty()) return null;
        var plantilla = seleccionada.get();
        var pasos = repository.pasos(plantilla.id()).stream().map(p -> new Paso(p.getOrden(), p.getDescripcion(),
            p.getIdPictograma() == null ? null : Pictograma.de(p.getIdPictograma(), p.getNombrePictograma(), p.getIdArasaac()))).toList();
        return new Plantilla(plantilla.id(), plantilla.nombre(), pasos);
    }
    private static boolean contiene(String query, String palabra) {
        return !palabra.isEmpty() && (" " + query + " ").contains(" " + palabra + " ");
    }
    private static String normalizar(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
