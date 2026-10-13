package dia_facil.backend.service.auth;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Identifiers only: the artwork belongs exclusively to the mobile application. */
public final class CatalogoAvatarCuidador {
    private static final Set<String> IDS = Stream.of("person", "animal")
        .flatMap(tipo -> IntStream.rangeClosed(1, 10).mapToObj(i -> String.format(Locale.ROOT, "%s-%02d", tipo, i)))
        .collect(Collectors.toUnmodifiableSet());
    private CatalogoAvatarCuidador() {}
    public static Set<String> ids() { return IDS; }
    public static void validar(String id) {
        if (id != null && !IDS.contains(id)) throw new IllegalArgumentException("Avatar no válido.");
    }
}
