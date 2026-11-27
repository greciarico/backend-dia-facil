package dia_facil.backend.web.perfiles;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.auth.*;
import dia_facil.backend.service.perfiles.*;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {VinculacionController.class, DispositivosPerfilController.class})
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class VinculacionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean VinculacionService service;
    @MockitoBean VinculacionTransaccionalService dispositivos;

    @Test void dispositivosRequiereSesionYUsaAlCuidadorAutenticado() throws Exception {
        String ruta = "/api/perfiles/12/dispositivos";
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(delete(ruta + "/4")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dispositivos);
        autenticar();
        when(dispositivos.listarDispositivos(3, 12)).thenReturn(java.util.List.of(
            new VinculacionTransaccionalService.DispositivoResumen(4, "Android", java.time.LocalDate.of(2026, 11, 26))));
        mvc.perform(get(ruta).header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dispositivos[0].idDispositivo").value(4))
            .andExpect(jsonPath("$.dispositivos[0].fechaVinculacion").value("2026-11-26"));
        mvc.perform(delete(ruta + "/4").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        verify(dispositivos).desvincular(3, 12, 4);
        doThrow(RechazoPerfil.noEncontrado()).when(dispositivos).desvincular(3, 12, 9);
        mvc.perform(delete(ruta + "/9").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isNotFound());
    }

    static final String ACCESS = "a_" + "A".repeat(43);
    void autenticar() { when(sesiones.autenticar(ACCESS)).thenReturn(new SesionCuidadorService.Identidad(1L,
        new SesionCuidadorService.CuidadorResumen(3, "Test", null))); }
    @Test void consultaSoloDesdePrincipalSinCacheNiGrafo() throws Exception {
        autenticar(); when(service.consultar(3, 12)).thenReturn(new VinculacionResumen("Persona", "DF-482157", 2, 0));
        mvc.perform(get("/api/perfiles/12/vinculacion").param("idCuidador", "999").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.codigoVinculacion").value("DF-482157"))
            .andExpect(jsonPath("$.nombrePerfil").value("Persona")).andExpect(jsonPath("$.dispositivosActivos").value(2))
            .andExpect(jsonPath("$.renovacionEnSegundos").value(0))
            .andExpect(jsonPath("$.idCuidador").doesNotExist()).andExpect(jsonPath("$.perfilUsuario").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        verify(service).consultar(3, 12);
    }
    @Test void postPrivadoUsaCodigoComoCondicionNoComoIdentidad() throws Exception {
        autenticar(); when(service.renovar(3, 12, "DF-482157")).thenReturn(new VinculacionResumen("Persona", "DF-7KQ9W6H2M8V3X5RA", 2, 60));
        mvc.perform(post("/api/perfiles/12/vinculacion/renovar-codigo").header("Authorization", "Bearer " + ACCESS)
            .contentType("application/json").content("{\"codigoActual\":\"DF-482157\",\"idCuidador\":999}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.codigoVinculacion").value("DF-7KQ9W6H2M8V3X5RA"))
            .andExpect(jsonPath("$.renovacionEnSegundos").value(60))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        verify(service).renovar(3, 12, "DF-482157");
    }
    @Test void requiereBearerEnAmbasRutas() throws Exception {
        mvc.perform(get("/api/perfiles/12/vinculacion")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/perfiles/12/vinculacion/renovar-codigo").contentType("application/json").content("{\"codigoActual\":\"DF-482157\"}"))
            .andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void respuestasSeguras404Y409() throws Exception {
        autenticar(); when(service.consultar(3, 12)).thenThrow(RechazoPerfil.noEncontrado());
        mvc.perform(get("/api/perfiles/12/vinculacion").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("PERFIL_NO_ENCONTRADO"));
        when(service.renovar(3, 12, "DF-482157")).thenThrow(new RechazoPerfil("CODIGO_VINCULACION_CAMBIADO", "Consulta el código actual.", null));
        mvc.perform(post("/api/perfiles/12/vinculacion/renovar-codigo").header("Authorization", "Bearer " + ACCESS)
            .contentType("application/json").content("{\"codigoActual\":\"DF-482157\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CODIGO_VINCULACION_CAMBIADO"));
    }
    @Test void renovacionPrematuraComunicaEsperaRealSinCache() throws Exception {
        autenticar(); when(service.renovar(3, 12, "DF-482157")).thenThrow(RechazoPerfil.renovacionPrematura(45));
        mvc.perform(post("/api/perfiles/12/vinculacion/renovar-codigo").header("Authorization", "Bearer " + ACCESS)
            .contentType("application/json").content("{\"codigoActual\":\"DF-482157\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.codigo").value("RENOVACION_PREMATURA"))
            .andExpect(jsonPath("$.renovacionEnSegundos").value(45)).andExpect(header().string("Retry-After", "45"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
}
