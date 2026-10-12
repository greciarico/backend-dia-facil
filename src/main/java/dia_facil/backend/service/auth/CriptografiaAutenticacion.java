package dia_facil.backend.service.auth;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Secrets and the clock are supplied by the eventual application assembly, never defaulted here. */
public class CriptografiaAutenticacion {
    private final Clock clock;
    private final SecureRandom random;
    private final byte[] claveOtp;
    private final byte[] claveRegistro;

    public CriptografiaAutenticacion(Clock clock, SecureRandom random, byte[] claveOtp, byte[] claveRegistro) {
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        if (claveOtp == null || claveRegistro == null || claveOtp.length < 32 || claveRegistro.length < 32
                || MessageDigest.isEqual(claveOtp, claveRegistro)) {
            throw new IllegalArgumentException("Se requieren dos claves distintas de al menos 32 bytes");
        }
        this.claveOtp = claveOtp.clone();
        this.claveRegistro = claveRegistro.clone();
    }

    public static String normalizarCorreo(String email) {
        if (email == null) throw new IllegalArgumentException("Correo obligatorio");
        var normalizado = email.strip().toLowerCase(Locale.ROOT);
        if (normalizado.isBlank() || normalizado.length() > 150) {
            throw new IllegalArgumentException("Correo invalido");
        }
        try {
            var address = new InternetAddress(normalizado, true);
            address.validate();
            if (address.getPersonal() != null || !normalizado.equals(address.getAddress())) {
                throw new IllegalArgumentException("Se requiere una unica direccion de correo");
            }
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Correo invalido");
        }
        return normalizado;
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public String generarCodigo() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    public String hashCodigo(String email, String codigo) {
        var contenido = ("diafacil:otp:v1\0" + email + "\0" + codigo).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(hmac(claveOtp, contenido));
    }

    public boolean coincideCodigo(String email, String codigo, String hash) {
        if (codigo == null || !codigo.matches("[0-9]{6}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(hash),
            HexFormat.of().parseHex(hashCodigo(email, codigo)));
    }

    public String emitirComprobante(String email, Integer idCodigo) {
        if (idCodigo == null || idCodigo <= 0) throw new IllegalArgumentException("Codigo persistido obligatorio");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(1);
                output.writeUTF("registro-cuidador");
                output.writeUTF(normalizarCorreo(email));
                output.writeInt(idCodigo);
                var emitido = clock.instant().truncatedTo(ChronoUnit.SECONDS);
                output.writeLong(emitido.getEpochSecond());
                output.writeLong(emitido.plusSeconds(600).getEpochSecond());
            }
            var contenido = bytes.toByteArray();
            var encoder = Base64.getUrlEncoder().withoutPadding();
            return encoder.encodeToString(contenido) + "." + encoder.encodeToString(hmac(claveRegistro, contenido));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo emitir el comprobante", ex);
        }
    }

    public Comprobante validarComprobante(String token) {
        if (token == null || token.length() > 2048) throw new IllegalArgumentException("Comprobante invalido");
        try {
            var partes = token.split("\\.", -1);
            if (partes.length != 2) throw new IllegalArgumentException("Comprobante invalido");
            var decoder = Base64.getUrlDecoder();
            var contenido = decoder.decode(partes[0]);
            var firma = decoder.decode(partes[1]);
            if (firma.length != 32 || !MessageDigest.isEqual(hmac(claveRegistro, contenido), firma)) {
                throw new IllegalArgumentException("Comprobante invalido");
            }
            try (var input = new DataInputStream(new ByteArrayInputStream(contenido))) {
                if (input.readInt() != 1 || !input.readUTF().equals("registro-cuidador")) {
                    throw new IllegalArgumentException("Comprobante invalido");
                }
                var email = input.readUTF();
                var id = input.readInt();
                var emitido = Instant.ofEpochSecond(input.readLong());
                var expiracion = Instant.ofEpochSecond(input.readLong());
                if (input.available() != 0 || id <= 0 || !email.equals(normalizarCorreo(email))
                        || !expiracion.equals(emitido.plusSeconds(600)) || clock.instant().isBefore(emitido)
                        || !clock.instant().isBefore(expiracion)) {
                    throw new IllegalArgumentException("Comprobante invalido o vencido");
                }
                return new Comprobante(email, id, expiracion);
            }
        } catch (IOException | java.time.DateTimeException ex) {
            throw new IllegalArgumentException("Comprobante invalido");
        }
    }

    public boolean vigente(Comprobante comprobante) {
        return clock.instant().isBefore(comprobante.expiracion());
    }

    private static byte[] hmac(byte[] clave, byte[] contenido) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(contenido);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular HMAC", ex);
        }
    }

    public record Comprobante(String email, Integer idCodigo, Instant expiracion) {}
}
