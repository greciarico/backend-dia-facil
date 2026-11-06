package dia_facil.backend.service.auth;

public final class SesionInvalidada extends RuntimeException {
    private final boolean renovable;
    public SesionInvalidada(boolean renovable) { super("Sesión no válida"); this.renovable = renovable; }
    public boolean renovable() { return renovable; }
}
