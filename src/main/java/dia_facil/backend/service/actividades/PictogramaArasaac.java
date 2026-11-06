package dia_facil.backend.service.actividades;

/** External identity, deliberately distinct from DiaFacil's generated pictogram ID. */
public record PictogramaArasaac(String fuente, Integer idArasaac, String nombre, String urlImagen) {
    public static String imagen(Integer id) {
        if (id == null) return null;
        if (id <= 0) throw new IllegalArgumentException();
        return "https://static.arasaac.org/pictograms/" + id + "/" + id + "_300.png";
    }
    public static PictogramaArasaac de(Integer id, String nombre) {
        return new PictogramaArasaac("ARASAAC", id, nombre, imagen(id));
    }
}
