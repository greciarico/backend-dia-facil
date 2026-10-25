package dia_facil.backend.web.actividades;

import dia_facil.backend.configuration.*;
import dia_facil.backend.service.actividades.AgendaUsuarioService;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.service.perfiles.PerfilResumen;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=AgendaUsuarioController.class,properties="diafacil.dispositivos.enabled=true")
@Import({SecurityConfiguration.class,DispositivoSecurityConfiguration.class,AutenticacionExceptionHandler.class})
class AgendaUsuarioControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccesoDispositivoService access;
    @MockitoBean AgendaUsuarioService agenda;
    static final String TOKEN="du_"+"D".repeat(43);
    @Test void detallePrivadoDevuelveSoloDefinicionBanderaYDiaSinPasosNiDatosInternos() throws Exception {
        var identity=new AccesoDispositivoService.Identidad(1L,12);
        when(access.autenticar(TOKEN)).thenReturn(identity);
        var activity=new dia_facil.backend.service.actividades.ActividadDetalle(7,"Desayunar","07:00","bread","PENDIENTE",false);
        when(agenda.detalle(identity,7)).thenReturn(new AgendaUsuarioService.Detalle(LocalDate.of(2026,10,24),"America/Lima",60,activity));
        mvc.perform(get("/api/usuario/actividades/7").param("idPerfilUsuario","999").param("fecha","2000-01-01").header("Authorization","Bearer "+TOKEN))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.fecha").value("2026-10-24")).andExpect(jsonPath("$.actividad.idActividad").value(7))
            .andExpect(jsonPath("$.actividad.guiaInicialCompletada").value(false)).andExpect(jsonPath("$.actividad.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.actividad.pasos").doesNotExist()).andExpect(jsonPath("$.actividad.perfilUsuario").doesNotExist())
            .andExpect(jsonPath("$.actividad.ejecuciones").doesNotExist());
        verify(agenda).detalle(identity,7);
    }
    @Test void detalleNoEncontradoDevuelve404FuncionalYNo404DeRuta() throws Exception {
        var identity=new AccesoDispositivoService.Identidad(1L,12);when(access.autenticar(TOKEN)).thenReturn(identity);
        when(agenda.detalle(identity,7)).thenThrow(dia_facil.backend.service.actividades.RechazoActividad.noEncontrada());
        mvc.perform(get("/api/usuario/actividades/7").header("Authorization","Bearer "+TOKEN))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ACTIVIDAD_NO_ENCONTRADA"));
    }
    @Test void detalleRequiereRolUsuarioYNoExponeComandosDeEjecucion() throws Exception {
        when(access.autenticar(TOKEN)).thenReturn(new AccesoDispositivoService.Identidad(1L,null));
        mvc.perform(get("/api/usuario/actividades/7").header("Authorization","Bearer "+TOKEN)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuario/actividades/7")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuario/actividades/7").header("Authorization","Bearer a_"+"A".repeat(43))).andExpect(status().isUnauthorized());
        when(access.autenticar(TOKEN)).thenThrow(RechazoDispositivo.invalida());
        mvc.perform(get("/api/usuario/actividades/7").header("Authorization","Bearer "+TOKEN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(agenda);
    }
    @Test void privadoUsuarioIgnoraAutoridadDeParametrosYNoCachea() throws Exception {
        var identity=new AccesoDispositivoService.Identidad(1L,12);
        when(access.autenticar(TOKEN)).thenReturn(identity);
        when(agenda.consultar(identity)).thenReturn(new AgendaUsuarioService.Agenda(LocalDate.of(2026,10,24),"America/Lima",60,new PerfilResumen(12,"Persona",null),List.of()));
        mvc.perform(get("/api/usuario/agenda").param("idPerfilUsuario","999").param("idCuidador","999").header("Authorization","Bearer "+TOKEN))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.perfil.idPerfilUsuario").value(12)).andExpect(jsonPath("$.fecha").value("2026-10-24"))
            .andExpect(jsonPath("$.actividades").isEmpty());
        verify(agenda).consultar(identity);
    }
    @Test void rechazaPendienteCuidadorYAnonimo() throws Exception {
        when(access.autenticar(TOKEN)).thenReturn(new AccesoDispositivoService.Identidad(1L,null));
        mvc.perform(get("/api/usuario/agenda").header("Authorization","Bearer "+TOKEN)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuario/agenda").header("Authorization","Bearer a_"+"A".repeat(43))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuario/agenda")).andExpect(status().isUnauthorized());
        verifyNoInteractions(agenda);
    }
    @Test void revocacionImpideConsultarAgenda() throws Exception {
        when(access.autenticar(TOKEN)).thenThrow(RechazoDispositivo.invalida());
        mvc.perform(get("/api/usuario/agenda").header("Authorization","Bearer "+TOKEN)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("VINCULACION_NO_VALIDA"));verifyNoInteractions(agenda);
    }
}
