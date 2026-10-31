package dia_facil.backend.service.perfiles;
import dia_facil.backend.service.avatares.CatalogoAvatares;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrearPerfilServiceTest {
    @Test void recortaSinRestringirCaracteresYAdmiteSesenta() {
        var tx = mock(CreacionPerfilTransaccionalService.class); var service = new CrearPerfilService(tx);
        service.crear(3, "  María-José 李 7  ", null); verify(tx).crear(3, "María-José 李 7", null);
        service.crear(3, "x".repeat(60), null); verify(tx).crear(3, "x".repeat(60), null);
        for (var name : new String[] {null, "", " \t\n", "x".repeat(61)}) assertThrows(RechazoPerfil.class, () -> service.crear(3, name, null));
        verifyNoMoreInteractions(tx);
    }
    @Test void comparteVeinteIdsYRechazaOtrosFormatosAntesDeAbrirTransaccion() {
        var tx = mock(CreacionPerfilTransaccionalService.class); var service = new CrearPerfilService(tx);
        for (var id : CatalogoAvatares.ids()) service.crear(3, "Persona", id);
        verify(tx, times(20)).crear(eq(3), eq("Persona"), anyString());
        for (var id : new String[] {"", "person-11", "person-1", "https://a.example/x", " person-01 "}) assertThrows(RechazoPerfil.class, () -> service.crear(3, "Persona", id));
        verifyNoMoreInteractions(tx);
    }
    private static DataIntegrityViolationException collision(String constraint) {
        return new DataIntegrityViolationException("reserved", new ConstraintViolationException("reserved", new SQLException("reserved", "23000", 1062), constraint));
    }
    @Test void soloColisionDeCodigoReintentaHastaTresOperacionesCompletas() {
        var tx = mock(CreacionPerfilTransaccionalService.class); var service = new CrearPerfilService(tx); var result = new PerfilResumen(12, "Persona", null);
        when(tx.crear(3, "Persona", null)).thenThrow(collision("perfil_usuario.uq_perfil_codigo")).thenReturn(result);
        assertEquals(result, service.crear(3, "Persona", null)); verify(tx, times(2)).crear(3, "Persona", null);
        reset(tx); when(tx.crear(3, "Persona", null)).thenThrow(collision("uq_perfil_codigo"));
        assertEquals("SERVICIO_NO_DISPONIBLE", assertThrows(RechazoPerfil.class, () -> service.crear(3, "Persona", null)).codigo()); verify(tx, times(3)).crear(3, "Persona", null);
        reset(tx); when(tx.crear(3, "Persona", null)).thenThrow(collision("otra_restriccion"));
        assertThrows(DataIntegrityViolationException.class, () -> service.crear(3, "Persona", null)); verify(tx).crear(3, "Persona", null);
    }
    @Test void traduceSenalConocidaDelTrigger() {
        var tx = mock(CreacionPerfilTransaccionalService.class);
        when(tx.crear(3, "Persona", null)).thenThrow(new DataIntegrityViolationException("reserved", new SQLException("Un cuidador puede administrar como maximo 4 perfiles", "45000", 1644)));
        assertEquals("LIMITE_PERFILES_ALCANZADO", assertThrows(RechazoPerfil.class, () -> new CrearPerfilService(tx).crear(3, "Persona", null)).codigo());
    }
    @Test void codigoTieneElFormatoEstable() {
        var generator = new GeneradorCodigoPerfil(); for (int i = 0; i < 100; i++) assertTrue(generator.generar().matches("DF-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{16}"));
    }
}
