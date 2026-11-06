package dia_facil.backend.service.perfiles;

public final class RechazoPerfil extends RuntimeException {
    private final String codigo;
    private final String campo;
    private final Long renovacionEnSegundos;
    public RechazoPerfil(String codigo, String mensaje, String campo) {
        this(codigo, mensaje, campo, null);
    }
    private RechazoPerfil(String codigo, String mensaje, String campo, Long renovacionEnSegundos) {
        super(mensaje); this.codigo = codigo; this.campo = campo; this.renovacionEnSegundos = renovacionEnSegundos;
    }
    public String codigo() { return codigo; }
    public String campo() { return campo; }
    public Long renovacionEnSegundos() { return renovacionEnSegundos; }
    public static RechazoPerfil renovacionPrematura(long segundos) {
        return new RechazoPerfil("RENOVACION_PREMATURA", "Espera antes de renovar nuevamente el código.", null, segundos);
    }
    public static RechazoPerfil noEncontrado() {
        return new RechazoPerfil("PERFIL_NO_ENCONTRADO", "No pudimos encontrar este perfil.", null);
    }
    public static RechazoPerfil limite() {
        return new RechazoPerfil("LIMITE_PERFILES_ALCANZADO", "Ya tienes el máximo de perfiles activos. Revisa Mis perfiles.", null);
    }
}
