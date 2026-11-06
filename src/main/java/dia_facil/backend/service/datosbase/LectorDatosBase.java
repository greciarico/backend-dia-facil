package dia_facil.backend.service.datosbase;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.Resource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

public final class LectorDatosBase {
    private LectorDatosBase() {}

    public static DatosBase leer(ObjectMapper mapper, Resource recurso) {
        try (var input = recurso.getInputStream()) {
            var datos = mapper.readerFor(DatosBase.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(input);
            validar((DatosBase) datos);
            return (DatosBase) datos;
        } catch (Exception ex) {
            throw new IllegalArgumentException("datos-base.json no es valido; no se inicializaron datos.", ex);
        }
    }

    public static void validar(DatosBase datos) {
        if (datos == null) throw invalido();
        lista(datos.iconos()); lista(datos.pictogramas()); lista(datos.plantillas());
        var iconos = new HashSet<String>(); var pictogramas = new HashSet<String>(); var plantillas = new HashSet<String>();
        for (var icono : datos.iconos()) {
            if (icono == null) throw invalido();
            clave(iconos, icono.codigo(), 60); texto(icono.nombre(), 80); palabras(icono.palabras());
        }
        for (var pictograma : datos.pictogramas()) {
            if (pictograma == null) throw invalido();
            texto(pictograma.nombre(), 80);
            clave(pictogramas, pictograma.clave(), 80);
            if (pictograma.idArasaac() != null) {
                if (pictograma.idArasaac() <= 0 || !"ARASAAC".equals(pictograma.fuente()) || pictograma.urlImagen() != null) throw invalido();
            } else {
                texto(pictograma.urlImagen(), 255);
                if ("ARASAAC".equals(pictograma.fuente())) throw invalido();
            }
            if (pictograma.fuente() != null) texto(pictograma.fuente(), 60);
            palabras(pictograma.palabras());
        }
        for (var plantilla : datos.plantillas()) {
            if (plantilla == null) throw invalido();
            clave(plantillas, plantilla.nombre(), 80); referencia(iconos, plantilla.icono());
            palabras(plantilla.palabras()); lista(plantilla.pasos()); var ordenes = new HashSet<Integer>();
            for (var paso : plantilla.pasos()) {
                if (paso == null || paso.orden() == null || paso.orden() < 0 || paso.orden() > 65535 || !ordenes.add(paso.orden())) throw invalido();
                texto(paso.descripcion(), 120); referencia(pictogramas, paso.pictograma());
            }
        }
    }
    private static void referencia(Set<String> claves, String valor) {
        if (valor != null && !claves.contains(comparable(valor))) throw invalido();
    }
    private static void palabras(List<String> palabras) {
        lista(palabras); var vistas = new HashSet<String>();
        palabras.forEach(palabra -> clave(vistas, palabra, 60));
    }
    private static void lista(List<?> lista) { if (lista == null) throw invalido(); }
    private static void clave(Set<String> claves, String valor, int maximo) {
        texto(valor, maximo); if (!claves.add(comparable(valor))) throw invalido();
    }
    private static void texto(String valor, int maximo) {
        if (valor == null || valor.isBlank() || valor.length() > maximo || !valor.equals(valor.strip())) throw invalido();
    }
    static String comparable(String valor) {
        // Spanish base data under utf8mb4_unicode_ci: do not mutate the stored spelling.
        return Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    private static IllegalArgumentException invalido() { return new IllegalArgumentException("Definicion de datos base invalida."); }
}
