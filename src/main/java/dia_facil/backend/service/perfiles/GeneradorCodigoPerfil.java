package dia_facil.backend.service.perfiles;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class GeneradorCodigoPerfil {
    private final SecureRandom random = new SecureRandom();
    private static final String ALFABETO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public String generar() {
        var codigo = new StringBuilder("DF-");
        for (int i = 0; i < 16; i++) codigo.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        return codigo.toString();
    }
}
