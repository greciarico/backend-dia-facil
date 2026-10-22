package dia_facil.backend.web.actividades;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.actividades.IconoActividadService;
import dia_facil.backend.service.auth.*;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
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

@WebMvcTest(controllers = IconoActividadController.class)
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class IconoActividadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean IconoActividadService iconos;
    private static final String ACCESS = "a_" + "A".repeat(43);
    private void autenticar() {
        when(sesiones.autenticar(ACCESS)).thenReturn(new SesionCuidadorService.Identidad(1L,
            new SesionCuidadorService.CuidadorResumen(3, "Cuidador", null)));
    }

    @Test void requiereBearerYNoAbreOtrosMetodosNiRutas() throws Exception {
        mvc.perform(get("/api/iconos-actividad")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/iconos-actividad").param("accessToken", ACCESS)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/iconos-actividad")).andExpect(status().isForbidden());
        mvc.perform(get("/api/iconos-actividad/extra")).andExpect(status().isForbidden());
        verifyNoInteractions(iconos);
    }

    @Test void devuelveDtoMinimoSinCacheYSeparaCatalogoDeConsulta() throws Exception {
        autenticar(); when(iconos.consultar(null)).thenReturn(List.of(new IconoActividadService.Icono("bread", "Pan")));
        when(iconos.consultar("sin coincidencia")).thenReturn(List.of());
        mvc.perform(get("/api/iconos-actividad").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(content().json("{\"iconos\":[{\"codigo\":\"bread\",\"nombre\":\"Pan\"}]}"))
            .andExpect(jsonPath("$.iconos[0].idIconoActividad").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/iconos-actividad").param("nombre", "sin coincidencia").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.iconos").isEmpty());
        verify(iconos).consultar(null); verify(iconos).consultar("sin coincidencia");
    }

    @Test void entradaInvalidaYFalloBdNoExponenDetalles() throws Exception {
        autenticar(); when(iconos.consultar("a".repeat(81))).thenThrow(new IllegalArgumentException("private"));
        mvc.perform(get("/api/iconos-actividad").param("nombre", "a".repeat(81)).header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        when(iconos.consultar(null)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private"));
        mvc.perform(get("/api/iconos-actividad").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }
}
