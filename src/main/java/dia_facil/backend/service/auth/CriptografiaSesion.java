package dia_facil.backend.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class CriptografiaSesion {
    private final SecureRandom random;
    public CriptografiaSesion(SecureRandom random) { this.random = random; }
    public String generar(String tipo) {
        var bytes = new byte[32]; random.nextBytes(bytes);
        return tipo + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static boolean valido(String token, String tipo) { return token != null && token.matches(tipo + "_[A-Za-z0-9_-]{43}"); }
    public static byte[] hash(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("Hash de sesión no disponible"); }
    }
}
