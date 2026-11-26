package dia_facil.backend.web.actividades;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.actividades.EdicionActividadService;
import dia_facil.backend.service.actividades.RechazoActividad;
import dia_facil.backend.service.auth.SesionCuidadorService;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EdicionActividadController.class)
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class EdicionActividadControllerTest {
    private static final String URL = "/api/perfiles/12/actividades/7";
    private static final String ACCESS = "a_" + "A".repeat(43);
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean EdicionActividadService service;

    @Test void consultaYActualizacionUsanPrincipalYComunicanEstadoAlGuardar() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        when(sesiones.autenticar(ACCESS)).thenReturn(new SesionCuidadorService.Identidad(1L,
            new SesionCuidadorService.CuidadorResumen(3, "Cuidador", null)));
        when(service.consultar(3, 12, 7)).thenReturn(detalle(1, "PENDIENTE"));
        when(service.editar(eq(3), eq(12), eq(7), any())).thenReturn(detalle(2, "EN_PROGRESO"));
        mvc.perform(get(URL).header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.pasos[0].idPasoActividad").value(9));
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2))
            .andExpect(jsonPath("$.estadoHoy").value("EN_PROGRESO"));
        when(service.editar(eq(3), eq(12), eq(7), any())).thenThrow(new RechazoActividad("ACTIVIDAD_MODIFICADA", "Cambió.", null));
        mvc.perform(solicitud()).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ACTIVIDAD_MODIFICADA"));
        when(service.consultar(3, 12, 7)).thenThrow(RechazoActividad.noEncontrada());
        mvc.perform(get(URL).header("Authorization", "Bearer " + ACCESS)).andExpect(status().isNotFound());
    }

    private static EdicionActividadService.Detalle detalle(int revision, String estado) {
        return new EdicionActividadService.Detalle(7, revision, "Rutina", "08:00", "bread", estado,
            List.of(new EdicionActividadService.Paso(9, "Paso", null)));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud() {
        return put(URL).header("Authorization", "Bearer " + ACCESS).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"revision":1,"nombre":"Rutina","hora":"08:00","codigoIcono":"bread","pasos":[{"descripcion":"Paso"}]}
                """);
    }
}
