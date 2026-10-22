package dia_facil.backend.service.datosbase;

import java.util.List;

/** Packaged functional data. References are natural keys, not generated database IDs. */
public record DatosBase(List<Icono> iconos, List<Pictograma> pictogramas, List<Plantilla> plantillas) {
    public record Icono(String codigo, String nombre, List<String> palabras) {}
    public record Pictograma(String nombre, String urlImagen, String fuente, List<String> palabras, Integer idArasaac) {
        public Pictograma(String nombre, String urlImagen, String fuente, List<String> palabras) {
            this(nombre, urlImagen, fuente, palabras, null);
        }
        public String clave() { return idArasaac == null ? nombre : "arasaac:" + idArasaac; }
    }
    public record Plantilla(String nombre, String icono, List<String> palabras, List<Paso> pasos) {}
    public record Paso(Integer orden, String descripcion, String pictograma) {}
}
