package dia_facil.backend.web.actividades;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.actividades.PlantillaActividadService;
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

@WebMvcTest(controllers = PlantillaActividadController.class)
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class PlantillaActividadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean PlantillaActividadService plantillas;
    private static final String ACCESS = "a_" + "A".repeat(43);
    private void autenticar() {
        when(sesiones.autenticar(ACCESS)).thenReturn(new SesionCuidadorService.Identidad(1L,
            new SesionCuidadorService.CuidadorResumen(3, "Cuidador", null)));
    }
    @Test void soloGetExactoConBearer() throws Exception {
        mvc.perform(get("/api/plantillas-actividad/sugerencia")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/plantillas-actividad/sugerencia").param("accessToken", ACCESS)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/plantillas-actividad/sugerencia")).andExpect(status().isForbidden());
        mvc.perform(get("/api/plantillas-actividad/1")).andExpect(status().isForbidden());
        verifyNoInteractions(plantillas);
    }
    @Test void nullYDtoMinimoSinUrlsNiEntidades() throws Exception {
        autenticar();
        mvc.perform(get("/api/plantillas-actividad/sugerencia").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(content().json("{\"plantilla\":null}"));
        when(plantillas.sugerir("Desayunar")).thenReturn(new PlantillaActividadService.Plantilla(4, "Desayunar", List.of(
            new PlantillaActividadService.Paso(1, "Lávate las manos", new PlantillaActividadService.Pictograma(9, "Lavarse las manos")))));
        mvc.perform(get("/api/plantillas-actividad/sugerencia").param("nombre", "Desayunar").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.plantilla.idPlantillaActividad").value(4))
            .andExpect(jsonPath("$.plantilla.pasos[0].descripcion").value("Lávate las manos"))
            .andExpect(jsonPath("$.plantilla.pasos[0].pictograma.idPictograma").value(9))
            .andExpect(jsonPath("$.plantilla.pasos[0].pictograma.urlImagen").doesNotExist())
            .andExpect(jsonPath("$.plantilla.pasos[0].idPlantillaPaso").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().doesNotExist("Set-Cookie"));
    }
    @Test void erroresSeguros() throws Exception {
        autenticar(); when(plantillas.sugerir("a".repeat(81))).thenThrow(new IllegalArgumentException("private"));
        mvc.perform(get("/api/plantillas-actividad/sugerencia").param("nombre", "a".repeat(81)).header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        when(plantillas.sugerir(null)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private"));
        mvc.perform(get("/api/plantillas-actividad/sugerencia").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }
}
