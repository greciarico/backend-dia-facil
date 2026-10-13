package dia_facil.backend.service.auth;

import dia_facil.backend.persistence.entity.CodigoAcceso;
import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.enums.EstadoCuidador;
import dia_facil.backend.persistence.repository.CodigoAccesoRepository;
import dia_facil.backend.persistence.repository.CuidadorRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static dia_facil.backend.service.auth.AutenticacionCuidadorService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutenticacionCuidadorTransaccionalServiceTest {
    static final String EMAIL = "grecia@example.com";
    static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    static CriptografiaAutenticacion crypto(Instant now) {
        byte[] otp = new byte[32], registro = new byte[32];
        Arrays.fill(otp, (byte) 1);
        Arrays.fill(registro, (byte) 2);
        return new CriptografiaAutenticacion(Clock.fixed(now, ZoneOffset.UTC),
            new SecureRandom() { @Override public int nextInt(int bound) { return 123; } }, otp, registro);
    }

    static class Fixture {
        final CuidadorRepository cuidadores = mock(CuidadorRepository.class);
        final CodigoAccesoRepository codigos = mock(CodigoAccesoRepository.class);
        final CriptografiaAutenticacion crypto = crypto(NOW);
        final AutenticacionCuidadorTransaccionalService servicio =
            new AutenticacionCuidadorTransaccionalService(cuidadores, codigos, crypto);

        CodigoAcceso codigo() {
            var codigo = CodigoAcceso.emitir(EMAIL, crypto.hashCodigo(EMAIL, "000123"), crypto.ahora().plusMinutes(10));
            ReflectionTestUtils.setField(codigo, "idCodigoAcceso", 7);
            ReflectionTestUtils.setField(codigo, "fechaCreacion", crypto.ahora().minusSeconds(45));
            when(codigos.findFirstByEmailOrderByFechaCreacionDescIdCodigoAccesoDesc(EMAIL))
                .thenReturn(Optional.of(codigo));
            return codigo;
        }
    }

    @Test void emisionInicialSoloGuardaHashYNoCreaCuenta() {
        var f = new Fixture();
        when(f.codigos.save(any())).thenAnswer(call -> {
            CodigoAcceso codigo = call.getArgument(0);
            assertEquals(f.crypto.hashCodigo(EMAIL, "000123"), codigo.getCodigoHash());
            assertEquals(f.crypto.ahora().plusMinutes(10), codigo.getFechaExpiracion());
            assertEquals(0, codigo.getIntentos());
            assertFalse(codigo.getUsado());
            assertNull(codigo.getIdCodigoAcceso());
            assertNull(codigo.getFechaCreacion());
            return codigo;
        });
        assertEquals("000123", f.servicio.emitirCodigo(EMAIL).codigo());
        verify(f.codigos).save(any());
        verifyNoInteractions(f.cuidadores);
    }

    @Test void reenvioRechazadoAntesDe45SegundosYPermitidoEnElLimite() {
        var f = new Fixture();
        var anterior = f.codigo();
        ReflectionTestUtils.setField(anterior, "fechaCreacion", f.crypto.ahora().minusSeconds(44));
        assertEquals(Motivo.REENVIO_PREMATURO,
            assertThrows(RechazoAutenticacion.class, () -> f.servicio.emitirCodigo(EMAIL)).getMotivo());
        verify(f.codigos, never()).save(any());
        ReflectionTestUtils.setField(anterior, "fechaCreacion", f.crypto.ahora().minusSeconds(45));
        f.servicio.emitirCodigo(EMAIL);
        verify(f.codigos).save(argThat(nuevo -> nuevo != anterior && nuevo.getIntentos() == 0));
        assertFalse(anterior.getUsado());
        verify(f.codigos, never()).delete(any());
    }

    @Test void codigoInexistenteNoModificaDatos() {
        var f = new Fixture();
        assertEquals(Motivo.CODIGO_INEXISTENTE, f.servicio.verificarCodigo(EMAIL, "000123").motivo());
        verify(f.codigos, never()).save(any());
        verifyNoInteractions(f.cuidadores);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"999999", "123", "abcdef", " 000123 "})
    void codigoIncorrectoOMalFormadoIncrementaUnIntento(String entrada) {
        var f = new Fixture();
        var codigo = f.codigo();
        assertEquals(Motivo.CODIGO_INCORRECTO, f.servicio.verificarCodigo(EMAIL, entrada).motivo());
        assertEquals(1, codigo.getIntentos());
        assertFalse(codigo.getUsado());
        verify(f.codigos).save(codigo);
        verifyNoInteractions(f.cuidadores);
    }

    @Test void quintoErrorPersisteYElCodigoCorrectoPosteriorSeRechaza() {
        var f = new Fixture();
        var codigo = f.codigo();
        codigo.setIntentos(4);
        assertEquals(Motivo.LIMITE_INTENTOS, f.servicio.verificarCodigo(EMAIL, "999999").motivo());
        assertEquals(5, codigo.getIntentos());
        assertEquals(Motivo.LIMITE_INTENTOS, f.servicio.verificarCodigo(EMAIL, "000123").motivo());
        verify(f.codigos, times(1)).save(codigo);
        verifyNoInteractions(f.cuidadores);
    }

    @Test void usadoYVencidoNoIncrementanIntentosNiRecuperanCodigosAnteriores() {
        var f = new Fixture();
        var codigo = f.codigo();
        codigo.setUsado(true);
        assertEquals(Motivo.CODIGO_USADO, f.servicio.verificarCodigo(EMAIL, "000123").motivo());
        codigo.setUsado(false);
        codigo.setFechaExpiracion(f.crypto.ahora());
        assertEquals(Motivo.CODIGO_VENCIDO, f.servicio.verificarCodigo(EMAIL, "000123").motivo());
        assertEquals(0, codigo.getIntentos());
        verify(f.codigos, never()).save(any());
        verify(f.codigos, never()).findById(any());
    }

    @Test void cuidadorActivoSeAutenticaYConsumeOtp() {
        var f = new Fixture();
        var codigo = f.codigo();
        var cuidador = Cuidador.crear(EMAIL, "Grecia");
        ReflectionTestUtils.setField(cuidador, "idCuidador", 3);
        when(f.cuidadores.findByEmail(EMAIL)).thenReturn(Optional.of(cuidador));
        var resultado = f.servicio.verificarCodigo(EMAIL, "000123");
        assertEquals(Estado.AUTENTICADO, resultado.estado());
        assertEquals(3, resultado.idCuidador());
        assertTrue(codigo.getUsado());
        verify(f.codigos).save(codigo);
        verify(f.cuidadores, never()).save(any());
    }

    @Test void cuidadorInactivoConsumeOtpSinReactivarse() {
        var f = new Fixture();
        var codigo = f.codigo();
        var cuidador = Cuidador.crear(EMAIL, "Grecia");
        cuidador.setEstado(EstadoCuidador.INACTIVO);
        when(f.cuidadores.findByEmail(EMAIL)).thenReturn(Optional.of(cuidador));
        assertEquals(Motivo.CUENTA_INACTIVA, f.servicio.verificarCodigo(EMAIL, "000123").motivo());
        assertTrue(codigo.getUsado());
        assertEquals(EstadoCuidador.INACTIVO, cuidador.getEstado());
        verify(f.codigos).save(codigo);
        verify(f.cuidadores, never()).save(any());
    }

    @Test void correoNuevoRecibeComprobanteSinPersistirCuidador() {
        var f = new Fixture();
        var codigo = f.codigo();
        var resultado = f.servicio.verificarCodigo(EMAIL, "000123");
        assertEquals(Estado.REGISTRO_PENDIENTE, resultado.estado());
        var comprobante = f.crypto.validarComprobante(resultado.comprobante());
        assertEquals(EMAIL, comprobante.email());
        assertEquals(7, comprobante.idCodigo());
        assertTrue(codigo.getUsado());
        verify(f.cuidadores, never()).save(any());
        verify(f.cuidadores, never()).saveAndFlush(any());
    }

    @Test void registroCreaCuentaCompletaActivaConCorreoFirmado() {
        var f = new Fixture();
        var codigo = f.codigo();
        codigo.setUsado(true);
        when(f.codigos.findById(7)).thenReturn(Optional.of(codigo));
        when(f.cuidadores.saveAndFlush(any())).thenAnswer(call -> {
            Cuidador cuidador = call.getArgument(0);
            assertEquals(EMAIL, cuidador.getEmail());
            assertEquals("Grecia", cuidador.getNombre());
            assertNull(cuidador.getAvatarId());
            assertEquals(EstadoCuidador.ACTIVO, cuidador.getEstado());
            assertNull(cuidador.getFechaCreacion());
            ReflectionTestUtils.setField(cuidador, "idCuidador", 3);
            return cuidador;
        });
        assertEquals(3, f.servicio.completarRegistro(new CriptografiaAutenticacion.Comprobante(
            EMAIL, 7, NOW.plusSeconds(600)), " Grecia "));
    }

    @Test void comprobanteDebeReferenciarCodigoConsumidoDelMismoCorreo() {
        var f = new Fixture();
        var codigo = f.codigo();
        var prueba = new CriptografiaAutenticacion.Comprobante(EMAIL, 7, NOW.plusSeconds(600));
        assertThrows(RechazoAutenticacion.class, () -> f.servicio.completarRegistro(prueba, "Grecia"));
        when(f.codigos.findById(7)).thenReturn(Optional.of(codigo));
        assertThrows(RechazoAutenticacion.class, () -> f.servicio.completarRegistro(prueba, "Grecia"));
        codigo.setUsado(true);
        codigo.setEmail("otro@example.com");
        assertThrows(RechazoAutenticacion.class, () -> f.servicio.completarRegistro(prueba, "Grecia"));
        verify(f.cuidadores, never()).saveAndFlush(any());
    }

    @Test void registroRecompruebaCaducidadYRechazaCuentaExistenteOComprobanteReutilizado() {
        var f = new Fixture();
        var codigo = f.codigo();
        codigo.setUsado(true);
        when(f.codigos.findById(7)).thenReturn(Optional.of(codigo));
        assertEquals(Motivo.COMPROBANTE_INVALIDO, assertThrows(RechazoAutenticacion.class,
            () -> f.servicio.completarRegistro(new CriptografiaAutenticacion.Comprobante(EMAIL, 7, NOW), "Grecia"))
            .getMotivo());
        when(f.cuidadores.findByEmail(EMAIL)).thenReturn(Optional.of(Cuidador.crear(EMAIL, "Grecia")));
        assertEquals(Motivo.CUENTA_EXISTENTE, assertThrows(RechazoAutenticacion.class,
            () -> f.servicio.completarRegistro(new CriptografiaAutenticacion.Comprobante(
                EMAIL, 7, NOW.plusSeconds(600)), "Otro nombre")).getMotivo());
        verify(f.cuidadores, never()).saveAndFlush(any());
    }

    @Test void entidadesRechazanConstruccionIncompleta() {
        assertThrows(IllegalArgumentException.class, () -> Cuidador.crear(EMAIL, null));
        assertThrows(IllegalArgumentException.class, () -> Cuidador.crear(EMAIL, " "));
        assertThrows(IllegalArgumentException.class, () -> CodigoAcceso.emitir(EMAIL, "otp", crypto(NOW).ahora()));
        assertThrows(IllegalArgumentException.class, () -> CodigoAcceso.emitir(EMAIL, "a".repeat(64), null));
    }
}
