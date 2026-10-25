package dia_facil.backend.service.dispositivos;

public final class RechazoDispositivo extends RuntimeException {
    private final int status; private final String codigo;
    public RechazoDispositivo(int status, String codigo, String mensaje) { super(mensaje); this.status = status; this.codigo = codigo; }
    public int status() { return status; } public String codigo() { return codigo; }
    public static RechazoDispositivo invalida() { return new RechazoDispositivo(401, "VINCULACION_NO_VALIDA", "Conecta nuevamente tu agenda."); }
    public static RechazoDispositivo codigoInvalido() { return new RechazoDispositivo(422, "CODIGO_VINCULACION_INVALIDO", "El código no es válido."); }
}
