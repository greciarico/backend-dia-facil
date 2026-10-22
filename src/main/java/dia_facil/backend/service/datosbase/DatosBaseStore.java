package dia_facil.backend.service.datosbase;

import java.util.List;
import java.util.Optional;

/** Only system catalogue operations. No updates/deletes or user entity access. */
public interface DatosBaseStore {
    record Icono(Integer id, String nombre) {}
    record Pictograma(Integer id, String urlImagen, String fuente, Integer idArasaac, String nombre) {
        public Pictograma(Integer id, String urlImagen, String fuente) { this(id, urlImagen, fuente, null, null); }
    }
    record Plantilla(Integer id, Integer icono) {}
    record Paso(Integer orden, String descripcion, Integer pictograma) {}
    Optional<Icono> icono(String codigo);
    Icono crearIcono(String codigo, String nombre);
    boolean palabraIcono(Integer icono, String palabra);
    void crearPalabraIcono(Integer icono, String palabra);
    Optional<Pictograma> pictograma(String nombre);
    Optional<Pictograma> pictogramaArasaac(Integer idArasaac);
    Pictograma crearPictograma(String nombre, String url, String fuente);
    Pictograma crearPictogramaArasaac(String nombre, Integer idArasaac);
    boolean palabraPictograma(Integer pictograma, String palabra);
    void crearPalabraPictograma(Integer pictograma, String palabra);
    Optional<Plantilla> plantilla(String nombre);
    Plantilla crearPlantilla(String nombre, Integer icono);
    List<Paso> pasos(Integer plantilla);
    void crearPaso(Integer plantilla, Integer orden, String descripcion, Integer pictograma);
    boolean palabraPlantilla(Integer plantilla, String palabra);
    void crearPalabraPlantilla(Integer plantilla, String palabra);
}
