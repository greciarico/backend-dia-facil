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
}
