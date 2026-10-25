package dia_facil.backend.service.actividades;

public class RechazoActividad extends RuntimeException {
    private final String codigo;
    private final String campo;
    public RechazoActividad(String codigo, String mensaje, String campo) { super(mensaje); this.codigo = codigo; this.campo = campo; }
    public String codigo() { return codigo; }
    public String campo() { return campo; }
    public static RechazoActividad noEncontrada() { return new RechazoActividad("ACTIVIDAD_NO_ENCONTRADA", "Esta actividad no está disponible.", null); }
    public static RechazoActividad datos(String campo, String mensaje) { return new RechazoActividad("SOLICITUD_INVALIDA", mensaje, campo); }
    public static RechazoActividad pictograma() { return new RechazoActividad("PICTOGRAMA_INVALIDO", "Selecciona otro pictograma o deja el paso sin imagen.", "pasos"); }
    public static RechazoActividad icono() { return new RechazoActividad("ICONO_NO_ENCONTRADO", "Selecciona un ícono del catálogo.", "codigoIcono"); }
}
