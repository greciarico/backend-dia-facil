package dia_facil.backend.web.auth;

import dia_facil.backend.service.auth.*;
import dia_facil.backend.service.fotos.FotosPerfilService;
import dia_facil.backend.service.fotos.CloudinaryFotos.Destino;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistroFotoControllerTest {
    @Test void comprobanteInvalidoNoAutorizaSubida() throws Exception {
        var auth = mock(AutenticacionCuidadorService.class);
        var fotos = mock(FotosPerfilService.class);
        doThrow(new AutenticacionCuidadorService.RechazoAutenticacion(AutenticacionCuidadorService.Motivo.COMPROBANTE_INVALIDO))
            .when(auth).validarRegistroFoto("pendiente", "Ana");
        var mvc = MockMvcBuilders.standaloneSetup(new RegistroFotoController(auth, fotos))
            .setControllerAdvice(new AutenticacionExceptionHandler()).build();
        mvc.perform(multipart("/api/auth/cuidadores/completar-registro")
            .file(new MockMultipartFile("foto", "perfil.jpg", "image/jpeg", new byte[]{1}))
            .param("comprobanteRegistro", "pendiente").param("nombre", "Ana"))
            .andExpect(jsonPath("$.codigo").value("COMPROBANTE_INVALIDO"));
        verifyNoInteractions(fotos);
    }

    @Test void multipartConservaContratoYConfirmaFoto() throws Exception {
        var auth = mock(AutenticacionCuidadorService.class);
        var fotos = mock(FotosPerfilService.class);
        var instante = Instant.now();
        var concesion = new SesionCuidadorService.Concesion(new SesionCuidadorService.CuidadorResumen(7, "Ana", null, true),
            new SesionCuidadorService.Tokens("Bearer", "acceso", instante, "renovacion", instante, instante));
        when(auth.registrarFoto("pendiente", "Ana", "referencia")).thenReturn(concesion);
        when(fotos.crear(eq(Destino.CUIDADORES), any(), any())).thenAnswer(call -> {
            java.util.function.Function<String, ?> crear = call.getArgument(2);
            return crear.apply("referencia");
        });
        var mvc = MockMvcBuilders.standaloneSetup(new RegistroFotoController(auth, fotos)).build();
        mvc.perform(multipart("/api/auth/cuidadores/completar-registro")
            .file(new MockMultipartFile("foto", "perfil.jpg", "image/jpeg", new byte[]{1}))
            .param("comprobanteRegistro", "pendiente").param("nombre", "Ana"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.cuidador.tieneFoto").value(true))
            .andExpect(jsonPath("$.cuidador.fotoReferencia").doesNotExist());
        var orden = inOrder(auth, fotos);
        orden.verify(auth).validarRegistroFoto("pendiente", "Ana");
        orden.verify(fotos).crear(eq(Destino.CUIDADORES), any(), any());
    }
}
