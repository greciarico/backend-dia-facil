package dia_facil.backend.service.auth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dia_facil.backend.service.auth.AutenticacionCuidadorTransaccionalServiceTest.*;

class CriptografiaAutenticacionTest {
    @Test void codigoConCerosYHashContextualDe64Caracteres() {
        var crypto = crypto(NOW);
        assertEquals("000123", crypto.generarCodigo());
        var hash = crypto.hashCodigo(EMAIL, "000123");
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertTrue(crypto.coincideCodigo(EMAIL, "000123", hash));
        assertFalse(crypto.coincideCodigo("otro@example.com", "000123", hash));
        assertFalse(crypto.coincideCodigo(EMAIL, "000124", hash));
    }

    @Test void comprobantePermaneceValidoAntesDelLimiteYExpiraExactamenteALos10Minutos() {
        var token = crypto(NOW).emitirComprobante(EMAIL, 7);
        assertEquals(EMAIL, crypto(NOW.plusSeconds(599)).validarComprobante(token).email());
        assertThrows(IllegalArgumentException.class, () -> crypto(NOW.plusSeconds(600)).validarComprobante(token));
        assertThrows(IllegalArgumentException.class, () -> crypto(NOW.minusSeconds(1)).validarComprobante(token));
    }

    @Test void rechazaAlteracionFirmaFormatoYPropositoAunqueEsteFirmado() throws Exception {
        var crypto = crypto(NOW);
        var token = crypto.emitirComprobante(EMAIL, 7);
        var partes = token.split("\\.");
        var contenido = Base64.getUrlDecoder().decode(partes[0]);
        contenido[0] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> crypto.validarComprobante(
            Base64.getUrlEncoder().withoutPadding().encodeToString(contenido) + "." + partes[1]));
        assertThrows(IllegalArgumentException.class, () -> crypto.validarComprobante(partes[0] + ".AA"));
        for (var invalido : new String[] {"", "a.b.c", "!.!", "x".repeat(2049)}) {
            assertThrows(IllegalArgumentException.class, () -> crypto.validarComprobante(invalido));
        }
        var entrada = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(partes[0])));
        var bytes = new ByteArrayOutputStream();
        var salida = new DataOutputStream(bytes);
        salida.writeInt(entrada.readInt());
        entrada.readUTF();
        salida.writeUTF("sesion");
        salida.write(entrada.readAllBytes());
        byte[] clave = new byte[32];
        Arrays.fill(clave, (byte) 2);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave, "HmacSHA256"));
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var otroProposito = encoder.encodeToString(bytes.toByteArray()) + "."
            + encoder.encodeToString(mac.doFinal(bytes.toByteArray()));
        assertThrows(IllegalArgumentException.class, () -> crypto.validarComprobante(otroProposito));
    }

    @Test void normalizaCorreoYRechazaDatosInvalidos() {
        assertEquals(EMAIL, CriptografiaAutenticacion.normalizarCorreo("  GRECIA@EXAMPLE.COM  "));
        for (var email : new String[] {"", "sin-correo", "a@example.com,b@example.com", "Nombre <a@example.com>"}) {
            assertThrows(IllegalArgumentException.class, () -> CriptografiaAutenticacion.normalizarCorreo(email));
        }
        assertThrows(IllegalArgumentException.class, () -> CriptografiaAutenticacion.normalizarCorreo(null));
    }

    @Test void exigeClavesSeparadasYSinValoresPredeterminados() {
        byte[] clave = new byte[32];
        assertThrows(IllegalArgumentException.class, () -> new CriptografiaAutenticacion(
            java.time.Clock.systemUTC(), new java.security.SecureRandom(), clave, clave));
        assertThrows(IllegalArgumentException.class, () -> new CriptografiaAutenticacion(
            java.time.Clock.systemUTC(), new java.security.SecureRandom(), null, clave));
    }
}
