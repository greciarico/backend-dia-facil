package dia_facil.backend.service.ayuda;
public class RechazoAyuda extends RuntimeException {
    private final int status; private final String codigo; private final long espera;
    public RechazoAyuda(int status,String codigo,String mensaje,long espera){super(mensaje);this.status=status;this.codigo=codigo;this.espera=espera;}
    public int status(){return status;} public String codigo(){return codigo;} public long espera(){return espera;}
    public static RechazoAyuda datos(){return new RechazoAyuda(400,"SOLICITUD_INVALIDA","Revisa los datos de la solicitud.",0);}
    public static RechazoAyuda contexto(){return new RechazoAyuda(409,"CONTEXTO_AYUDA_NO_VALIDO","Consulta nuevamente la actividad antes de pedir ayuda.",0);}
    public static RechazoAyuda noEncontrada(){return new RechazoAyuda(404,"SOLICITUD_NO_ENCONTRADA","No encontramos esta solicitud.",0);}
}
