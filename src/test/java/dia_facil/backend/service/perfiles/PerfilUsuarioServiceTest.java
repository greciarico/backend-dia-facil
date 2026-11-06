package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.PerfilUsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerfilUsuarioServiceTest {
    @Test void consultaActivosDelPropietarioSinTruncarNiInventarResultados() {
        var repo = mock(PerfilUsuarioRepository.class);
        var lista = List.of(new PerfilResumen(2, "Persona real", null), new PerfilResumen(8, "Otra persona", "person-01"));
        when(repo.consultar(3, EstadoPerfil.ACTIVO)).thenReturn(lista);
        var service = new PerfilUsuarioService(repo);
        assertEquals(lista, service.consultar(3));
        verify(repo).consultar(3, EstadoPerfil.ACTIVO); verifyNoMoreInteractions(repo);
    }
    @Test void vacioEsListaVaciaYLaConsultaEsSoloLectura() throws Exception {
        var repo = mock(PerfilUsuarioRepository.class);
        when(repo.consultar(7, EstadoPerfil.ACTIVO)).thenReturn(List.of());
        assertTrue(new PerfilUsuarioService(repo).consultar(7).isEmpty());
        assertTrue(PerfilUsuarioService.class.getMethod("consultar", Integer.class).getAnnotation(Transactional.class).readOnly());
        assertEquals(4, PoliticaPerfiles.MAXIMO_ACTIVOS);
    }
    @Test void detalleFiltraPorPropietarioIdYEstadoSinHacerBusquedaGlobal() throws Exception {
        var repo = mock(PerfilUsuarioRepository.class); var service = new PerfilUsuarioService(repo);
        var profile = new PerfilResumen(12, "Persona", null);
        when(repo.detalle(12, 3, EstadoPerfil.ACTIVO)).thenReturn(java.util.Optional.of(profile));
        when(repo.detalle(12, 20, EstadoPerfil.ACTIVO)).thenReturn(java.util.Optional.empty());
        assertEquals(profile, service.detalle(3, 12));
        assertEquals("PERFIL_NO_ENCONTRADO", assertThrows(RechazoPerfil.class, () -> service.detalle(20, 12)).codigo());
        assertThrows(RechazoPerfil.class, () -> service.detalle(3, 0));
        verify(repo).detalle(12, 3, EstadoPerfil.ACTIVO); verify(repo).detalle(12, 20, EstadoPerfil.ACTIVO); verifyNoMoreInteractions(repo);
        assertTrue(PerfilUsuarioService.class.getMethod("detalle", Integer.class, Integer.class).getAnnotation(Transactional.class).readOnly());
    }
}
