package dia_facil.backend.service.perfiles;

public final class RechazoPerfil extends RuntimeException {
    private final String codigo;
    private final String campo;
    public RechazoPerfil(String codigo, String mensaje, String campo) {
        super(mensaje); this.codigo = codigo; this.campo = campo;
    }
    public String codigo() { return codigo; }
    public String campo() { return campo; }
    public static RechazoPerfil noEncontrado() {
        return new RechazoPerfil("PERFIL_NO_ENCONTRADO", "No pudimos encontrar este perfil.", null);
    }
    public static RechazoPerfil limite() {
        return new RechazoPerfil("LIMITE_PERFILES_ALCANZADO", "Ya tienes el máximo de perfiles activos. Revisa Mis perfiles.", null);
    }
}
