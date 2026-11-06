package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.ActividadRepository;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.service.perfiles.PerfilResumen;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgendaUsuarioServiceTest {
    @Test void detalleUsaMismoDiaYPerfilRealConFlagIndependienteDelEstadoDiario() throws Exception {
        var access=mock(AccesoDispositivoService.class);var repo=mock(ActividadRepository.class);
        var principal=new AccesoDispositivoService.Identidad(3L,999);
        when(access.perfilActivo(principal)).thenReturn(new PerfilResumen(12,"Persona",null));
        var date=LocalDate.of(2026,10,23);
        var clock=Clock.fixed(Instant.parse("2026-10-24T04:59:59Z"),ZoneId.of("America/Lima"));
        var service=new AgendaUsuarioService(access,repo,clock);
        for(var estado:java.util.Arrays.asList(null,EstadoEjecucion.EN_PROGRESO,EstadoEjecucion.COMPLETADA)) {
            for(boolean guia:List.of(false,true)) {
                var activity=new ActividadDetalle(7,"Desayunar",LocalTime.of(7,0),"bread",estado,guia);
                when(repo.detalleUsuario(7,12,date,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA)).thenReturn(java.util.Optional.of(activity));
                var result=service.detalle(principal,7);
                assertEquals(date,result.fecha());assertEquals("America/Lima",result.zonaHoraria());assertEquals(1,result.actualizarEnSegundos());
                assertEquals(guia,result.actividad().guiaInicialCompletada());assertEquals("07:00",result.actividad().hora());
                assertEquals(estado==null?"PENDIENTE":estado.name(),result.actividad().estado());
            }
        }
        verify(repo,times(6)).detalleUsuario(7,12,date,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA);verifyNoMoreInteractions(repo);
        assertTrue(AgendaUsuarioService.class.getMethod("detalle",AccesoDispositivoService.Identidad.class,Integer.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly());
    }
    @Test void detalleInexistenteAjenoOInactivoComparteRechazoYCredencialInvalidaNoConsulta() {
        var access=mock(AccesoDispositivoService.class);var repo=mock(ActividadRepository.class);
        var principal=new AccesoDispositivoService.Identidad(3L,12);
        when(access.perfilActivo(principal)).thenReturn(new PerfilResumen(12,"Persona",null));
        when(repo.detalleUsuario(any(),any(),any(),any(),any())).thenReturn(java.util.Optional.empty());
        var service=new AgendaUsuarioService(access,repo,Clock.fixed(Instant.parse("2026-10-24T05:00:00Z"),ZoneId.of("America/Lima")));
        for(Integer id:List.of(0,-1,999))assertEquals("ACTIVIDAD_NO_ENCONTRADA",assertThrows(RechazoActividad.class,()->service.detalle(principal,id)).codigo());
        verify(repo).detalleUsuario(999,12,LocalDate.of(2026,10,24),EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA);
        reset(repo);when(access.perfilActivo(principal)).thenThrow(RechazoDispositivo.invalida());
        assertThrows(RechazoDispositivo.class,()->service.detalle(principal,7));verifyNoInteractions(repo);
    }
    @Test void diaLocalConsultaSoloPerfilAutenticadoYNoCreaEjecuciones() {
        var access=mock(AccesoDispositivoService.class);var repo=mock(ActividadRepository.class);
        var principal=new AccesoDispositivoService.Identidad(3L,12);
        when(access.perfilActivo(principal)).thenReturn(new PerfilResumen(12,"Persona",null));
        var date=LocalDate.of(2026,10,23);
        var activities=List.of(new ActividadAgenda(7,"Desayunar",LocalTime.of(7,0),"bread",null),
            new ActividadAgenda(8,"Leer",LocalTime.of(8,0),null,EstadoEjecucion.EN_PROGRESO),
            new ActividadAgenda(9,"Dormir",LocalTime.of(22,0),"bed",EstadoEjecucion.COMPLETADA));
        when(repo.agenda(12,date,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA)).thenReturn(activities);
        var clock=Clock.fixed(Instant.parse("2026-10-24T04:59:59Z"),ZoneId.of("America/Lima"));
        var result=new AgendaUsuarioService(access,repo,clock).consultar(principal);
        assertEquals(date,result.fecha());assertEquals("America/Lima",result.zonaHoraria());assertEquals(1,result.actualizarEnSegundos());
        assertEquals(List.of("PENDIENTE","EN_PROGRESO","COMPLETADA"),result.actividades().stream().map(ActividadAgenda::estado).toList());
        assertEquals("07:00",result.actividades().getFirst().hora());verify(repo).agenda(12,date,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA);verifyNoMoreInteractions(repo);
    }
    @Test void medianocheCambiaDiaYRechazoDeIdentidadNoConsultaActividades() {
        var access=mock(AccesoDispositivoService.class);var repo=mock(ActividadRepository.class);
        var principal=new AccesoDispositivoService.Identidad(3L,12);
        when(access.perfilActivo(principal)).thenReturn(new PerfilResumen(12,"Persona",null));
        when(repo.agenda(any(),any(),any(),any())).thenReturn(List.of());
        var clock=Clock.fixed(Instant.parse("2026-10-24T05:00:00Z"),ZoneId.of("America/Lima"));
        var service=new AgendaUsuarioService(access,repo,clock);
        assertEquals(LocalDate.of(2026,10,24),service.consultar(principal).fecha());
        assertEquals(86400,service.consultar(principal).actualizarEnSegundos());
        reset(repo);when(access.perfilActivo(principal)).thenThrow(RechazoDispositivo.invalida());
        assertThrows(RechazoDispositivo.class,()->service.consultar(principal));verifyNoInteractions(repo);
    }
}
