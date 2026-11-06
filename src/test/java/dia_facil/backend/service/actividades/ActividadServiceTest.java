package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.enums.EstadoActividad;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.ActividadRepository;
import dia_facil.backend.service.perfiles.PerfilUsuarioService;
import dia_facil.backend.service.perfiles.PerfilResumen;
import dia_facil.backend.service.perfiles.RechazoPerfil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActividadServiceTest {
    @Test void vacioRealYDatosRealesConsultanSoloActivasDelPerfilYPropietario() throws Exception {
        var perfiles = mock(PerfilUsuarioService.class); var repo = mock(ActividadRepository.class);
        var service = new ActividadService(perfiles, repo);
        when(perfiles.detalle(3, 12)).thenReturn(new PerfilResumen(12, "Persona", null));
        when(repo.consultar(12, 3, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA)).thenReturn(List.of());
        assertEquals(List.of(), service.consultar(3, 12));
        var reales = List.of(new ActividadResumen(7, "Desayunar"));
        when(repo.consultar(12, 3, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA)).thenReturn(reales);
        assertEquals(reales, service.consultar(3, 12));
        var orden = inOrder(perfiles, repo);
        orden.verify(perfiles).detalle(3, 12); orden.verify(repo).consultar(12, 3, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA);
        orden.verify(perfiles).detalle(3, 12); orden.verify(repo).consultar(12, 3, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA);
        verifyNoMoreInteractions(perfiles, repo);
        assertTrue(ActividadService.class.getMethod("consultar", Integer.class, Integer.class).getAnnotation(Transactional.class).readOnly());
    }

    @Test void perfilNoDisponibleNuncaSeConvierteEnVacioNiConsultaActividades() {
        var perfiles = mock(PerfilUsuarioService.class); var repo = mock(ActividadRepository.class);
        var service = new ActividadService(perfiles, repo);
        for (int id : new int[] {12, 13, 999, 0}) {
            when(perfiles.detalle(20, id)).thenThrow(RechazoPerfil.noEncontrado());
            assertEquals("PERFIL_NO_ENCONTRADO", assertThrows(RechazoPerfil.class, () -> service.consultar(20, id)).codigo());
        }
        verifyNoInteractions(repo);
    }
}
