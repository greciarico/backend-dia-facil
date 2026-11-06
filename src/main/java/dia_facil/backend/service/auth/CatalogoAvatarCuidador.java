package dia_facil.backend.service.auth;

import java.util.Set;

/** Identifiers only: the artwork belongs exclusively to the mobile application. */
public final class CatalogoAvatarCuidador {
    private CatalogoAvatarCuidador() {}
    public static Set<String> ids() { return dia_facil.backend.service.avatares.CatalogoAvatares.ids(); }
    public static void validar(String id) {
        dia_facil.backend.service.avatares.CatalogoAvatares.validar(id);
    }
}
