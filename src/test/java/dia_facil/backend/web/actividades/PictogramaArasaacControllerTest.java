package dia_facil.backend.web.actividades;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.actividades.*;
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

@WebMvcTest(controllers = PictogramaArasaacController.class)
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class PictogramaArasaacControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean PictogramaArasaacService pictogramas;
    private static final String ACCESS = "a_" + "A".repeat(43);
    private void autenticar() {
        when(sesiones.autenticar(ACCESS)).thenReturn(new SesionCuidadorService.Identidad(1L,
            new SesionCuidadorService.CuidadorResumen(3, "Cuidador", null)));
    }
    @Test void soloGetExactoConBearer() throws Exception {
        mvc.perform(get("/api/pictogramas/arasaac")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pictogramas/arasaac").param("idCuidador", "3")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pictogramas/arasaac")).andExpect(status().isForbidden());
        mvc.perform(get("/api/pictogramas/all")).andExpect(status().isForbidden());
        verifyNoInteractions(pictogramas);
    }
    @Test void metadataMinimaSinPersistirNiExponerProveedorCompleto() throws Exception {
        autenticar(); when(pictogramas.buscar("lavar")).thenReturn(List.of(PictogramaArasaac.de(123, "lavar")));
        mvc.perform(get("/api/pictogramas/arasaac").param("texto", "lavar").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pictogramas[0].idArasaac").value(123))
            .andExpect(jsonPath("$.pictogramas[0].urlImagen").value("https://static.arasaac.org/pictograms/123/123_300.png"))
            .andExpect(jsonPath("$.pictogramas[0].idPictograma").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().doesNotExist("Set-Cookie"));
    }
    @Test void errorRecuperableDistintoDeListaVacia() throws Exception {
        autenticar(); when(pictogramas.buscar("lavar")).thenThrow(new ArasaacClient.NoDisponible());
        mvc.perform(get("/api/pictogramas/arasaac").param("texto", "lavar").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("PICTOGRAMAS_NO_DISPONIBLES"));
    }
}
