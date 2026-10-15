package dia_facil.backend.service.perfiles;

import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class GeneradorCodigoPerfil {
    private final SecureRandom random = new SecureRandom();
    public String generar() { return String.format(Locale.ROOT, "DF-%06d", random.nextInt(1_000_000)); }
}
