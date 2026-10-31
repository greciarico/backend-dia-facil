package dia_facil.backend.web.dispositivos;

import dia_facil.backend.configuration.*;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real disabled configuration: deliberately no mock of the conditional service. */
@WebMvcTest(controllers=DispositivoController.class,properties="diafacil.dispositivos.enabled=false")
@Import({SecurityConfiguration.class,DispositivoSecurityConfiguration.class})
class DispositivoDesactivadoTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test void preparacionPublicaDesactivadaDevuelve503SinBuscarRecursoEstatico() throws Exception {
        assertTrue(context.getBeansOfType(AccesoDispositivoService.class).isEmpty());
        assertTrue(context.getBeansOfType(DispositivoController.class).isEmpty());
        mvc.perform(post("/api/dispositivos/preparar-vinculacion"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(result -> assertNull(result.getResolvedException()));
    }

    @Test void rutasDeVinculacionDesactivadasTambienResponden503() throws Exception {
        mvc.perform(post("/api/dispositivos/vincular").contentType("application/json").content("{}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
        mvc.perform(get("/api/dispositivos/vinculacion"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }
}
