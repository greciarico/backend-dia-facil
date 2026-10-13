package dia_facil.backend.service.perfiles;

/** The future create/reactivate command must enforce this policy transactionally. */
public final class PoliticaPerfiles {
    public static final int MAXIMO_ACTIVOS = 4;
    private PoliticaPerfiles() {}
}
