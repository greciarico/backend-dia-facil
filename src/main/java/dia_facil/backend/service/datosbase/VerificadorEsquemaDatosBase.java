package dia_facil.backend.service.datosbase;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.hibernate.Session;

public class VerificadorEsquemaDatosBase {
    record Unica(String tabla, String nombre, List<String> columnas) {}
    static final List<Unica> REQUERIDAS = List.of(
        new Unica("icono_actividad", "uq_icono_actividad_codigo", List.of("codigo")),
        new Unica("pictograma", "uq_pictograma_arasaac", List.of("id_arasaac")),
        new Unica("plantilla_actividad", "uq_plantilla_nombre", List.of("nombre")),
        new Unica("palabra_clave_icono_actividad", "uq_palabra_icono_actividad", List.of("id_icono_actividad", "palabra")),
        new Unica("palabra_clave_pictograma", "uq_palabra_pictograma", List.of("id_pictograma", "palabra")),
        new Unica("palabra_clave_actividad", "uq_palabra_actividad", List.of("id_plantilla_actividad", "palabra")),
        new Unica("plantilla_paso", "uq_plantilla_paso_orden", List.of("id_plantilla_actividad", "orden")));
    private final EntityManager em;
    public VerificadorEsquemaDatosBase(EntityManager em) { this.em = em; }
    public void verificar() { em.unwrap(Session.class).doWork(VerificadorEsquemaDatosBase::verificar); }

    static void verificar(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        for (var requerida : REQUERIDAS) {
            Map<String, TreeMap<Short, String>> indices = new HashMap<>();
            try (var rows = metadata.getIndexInfo(connection.getCatalog(), null, requerida.tabla(), true, false)) {
                while (rows.next()) {
                    var nombre = rows.getString("INDEX_NAME"); var columna = rows.getString("COLUMN_NAME");
                    if (nombre != null && columna != null && !rows.getBoolean("NON_UNIQUE")) {
                        indices.computeIfAbsent(nombre.toLowerCase(Locale.ROOT), key -> new TreeMap<>())
                            .put(rows.getShort("ORDINAL_POSITION"), columna.toLowerCase(Locale.ROOT));
                    }
                }
            }
            var columnas = indices.get(requerida.nombre());
            if (columnas == null || !List.copyOf(columnas.values()).equals(requerida.columnas())) {
                throw new IllegalStateException("Falta UNIQUE " + requerida.nombre() + " en " + requerida.tabla()
                    + ". Revise la auditoria/transicion de datos base antes de iniciar. No se escribieron datos base.");
            }
        }
    }
}
