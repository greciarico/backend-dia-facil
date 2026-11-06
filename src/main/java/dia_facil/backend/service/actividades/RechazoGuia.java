package dia_facil.backend.service.actividades;

public class RechazoGuia extends RuntimeException {
    private final int status;
    private final String codigo;
    public RechazoGuia(int status, String codigo, String mensaje) { super(mensaje); this.status=status;this.codigo=codigo; }
    public int status(){return status;}
    public String codigo(){return codigo;}
    public static RechazoGuia noDisponible(){return new RechazoGuia(409,"GUIA_NO_DISPONIBLE","No pudimos abrir los pasos de esta actividad.");}
    public static RechazoGuia dia(){return new RechazoGuia(409,"DIA_AGENDA_CAMBIADO","Comenzó un nuevo día. Vuelve al detalle de la actividad.");}
    public static RechazoGuia inicial(){return new RechazoGuia(409,"GUIA_INICIAL_REQUERIDA","Primero debes completar todos los pasos de la guía.");}
    public static RechazoGuia paso(){return new RechazoGuia(409,"RECORRIDO_NO_PERMITIDO","Continúa desde un paso que ya hayas alcanzado.");}
    public static RechazoGuia ejecucion(){return new RechazoGuia(404,"EJECUCION_NO_ENCONTRADA","Esta ejecución no está disponible.");}
}
