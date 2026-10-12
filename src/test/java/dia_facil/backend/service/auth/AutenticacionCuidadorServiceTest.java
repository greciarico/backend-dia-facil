package dia_facil.backend.service.auth;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import static dia_facil.backend.service.auth.AutenticacionCuidadorTransaccionalServiceTest.*;
import static dia_facil.backend.service.auth.AutenticacionCuidadorService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutenticacionCuidadorServiceTest {
    @Test void normalizaCorreoYSoloEntregaCodigoAlContratoDeEnvio() {
        var f = new Fixture();
        var envio = mock(EnvioCodigoAcceso.class);
        var service = new AutenticacionCuidadorService(f.servicio, f.crypto, envio);
        service.solicitarCodigo(" GRECIA@EXAMPLE.COM ");
        verify(f.codigos).findFirstByEmailOrderByFechaCreacionDescIdCodigoAccesoDesc(EMAIL);
        verify(envio).enviar(EMAIL, "000123");
    }

    @Test void falloDePersistenciaImpideEnvioYFalloDeEnvioNoSeOculta() {
        var f = new Fixture();
        var envio = mock(EnvioCodigoAcceso.class);
        var service = new AutenticacionCuidadorService(f.servicio, f.crypto, envio);
        when(f.codigos.save(any())).thenThrow(new IllegalStateException("Persistencia no disponible"));
        assertThrows(IllegalStateException.class, () -> service.solicitarCodigo(EMAIL));
        verifyNoInteractions(envio);
        reset(f.codigos);
        doThrow(new IllegalStateException("Envio no disponible")).when(envio).enviar(any(), any());
        assertThrows(IllegalStateException.class, () -> service.solicitarCodigo(EMAIL));
        verify(f.codigos).save(any());
    }

    @Test void validacionDeNombreYComprobanteOcurreAntesDePersistir() {
        var f = new Fixture();
        var service = new AutenticacionCuidadorService(f.servicio, f.crypto, mock(EnvioCodigoAcceso.class));
        var token = f.crypto.emitirComprobante(EMAIL, 7);
        for (var nombre : new String[] {"", " ", "a".repeat(101)}) {
            assertThrows(IllegalArgumentException.class, () -> service.completarRegistro(token, nombre));
        }
        assertThrows(IllegalArgumentException.class, () -> service.completarRegistro(token, null));
        assertThrows(IllegalArgumentException.class, () -> service.completarRegistro("alterado", "Grecia"));
        verifyNoInteractions(f.codigos, f.cuidadores);
    }

    @Test void soloTraduceLaRestriccionUnicaDeCorreoYNoOtrosFallos() {
        var f = new Fixture();
        var codigo = f.codigo();
        codigo.setUsado(true);
        when(f.codigos.findById(7)).thenReturn(java.util.Optional.of(codigo));
        var service = new AutenticacionCuidadorService(f.servicio, f.crypto, mock(EnvioCodigoAcceso.class));
        var token = f.crypto.emitirComprobante(EMAIL, 7);
        var duplicado = new DataIntegrityViolationException("Duplicado", new SQLException(
            "Duplicate entry for key 'cuidador.uq_cuidador_email'", "23000", 1062));
        when(f.cuidadores.saveAndFlush(any())).thenThrow(duplicado);
        assertEquals(Motivo.CUENTA_EXISTENTE, assertThrows(RechazoAutenticacion.class,
            () -> service.completarRegistro(token, "Grecia")).getMotivo());
        var otro = new DataIntegrityViolationException("Otro", new SQLException("Otra restriccion", "23000", 1048));
        doThrow(otro).when(f.cuidadores).saveAndFlush(any());
        assertSame(otro, assertThrows(DataIntegrityViolationException.class,
            () -> service.completarRegistro(token, "Grecia")));
        var parecido = new DataIntegrityViolationException("Otra unica", new SQLException(
            "Duplicate entry for key 'uq_cuidador_email_backup'", "23000", 1062));
        doThrow(parecido).when(f.cuidadores).saveAndFlush(any());
        assertSame(parecido, assertThrows(DataIntegrityViolationException.class,
            () -> service.completarRegistro(token, "Grecia")));
    }
}
