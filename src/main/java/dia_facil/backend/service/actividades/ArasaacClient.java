package dia_facil.backend.service.actividades;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Only metadata is fetched here. Never forwards session headers or accepts arbitrary URLs. */
@Component
public class ArasaacClient {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private final RestClient client;
    private final ObjectMapper mapper;

    @Autowired
    public ArasaacClient(ObjectMapper mapper) {
        this(crearCliente(), mapper);
    }
    ArasaacClient(RestClient client, ObjectMapper mapper) { this.client = client; this.mapper = mapper; }
    private static RestClient crearCliente() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        return RestClient.builder().baseUrl("https://api.arasaac.org/v1").requestFactory(factory).build();
    }
    public List<PictogramaArasaac> buscar(String texto) {
        try {
            return client.get().uri("/pictograms/es/search/{texto}", texto).accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> {
                    // ARASAAC may report an unmatched search with 404.
                    if (response.getStatusCode().value() == 404) return List.of();
                    if (response.getStatusCode().value() != 200) throw new NoDisponible();
                    var bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (bytes.length > MAX_RESPONSE_BYTES) throw new NoDisponible();
                    return interpretar(mapper.readTree(bytes));
                });
        } catch (RuntimeException error) {
            // Do not retain upstream exceptions: their messages can include the step text/URI.
            throw new NoDisponible();
        }
    }
    static List<PictogramaArasaac> interpretar(JsonNode raiz) {
        if (raiz == null || !raiz.isArray()) throw new NoDisponible();
        var resultado = new ArrayList<PictogramaArasaac>(); var ids = new HashSet<Integer>();
        for (var nodo : raiz) {
            var id = nodo.path("_id"); var palabras = nodo.path("keywords");
            if (!id.isIntegralNumber() || !id.canConvertToInt() || id.intValue() <= 0 || !palabras.isArray()) throw new NoDisponible();
            String nombre = null;
            for (var palabra : palabras) {
                var valor = palabra.path("keyword");
                if (valor.isString() && !valor.asString().isBlank()) { nombre = valor.asString().strip(); break; }
            }
            if (nombre == null) throw new NoDisponible();
            // Local labels fit Pictograma.nombre; external keywords are not copied to our catalogue.
            if (nombre.length() > 80) nombre = nombre.substring(0, 80);
            if (ids.add(id.intValue())) resultado.add(PictogramaArasaac.de(id.intValue(), nombre));
        }
        return List.copyOf(resultado);
    }
    public static final class NoDisponible extends RuntimeException {
        public NoDisponible() { super("No pudimos consultar los pictogramas. Inténtalo de nuevo."); }
    }
}
