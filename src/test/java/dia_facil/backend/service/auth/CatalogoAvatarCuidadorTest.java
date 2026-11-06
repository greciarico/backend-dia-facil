package dia_facil.backend.service.auth;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static dia_facil.backend.service.auth.AutenticacionCuidadorTransaccionalServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogoAvatarCuidadorTest {
    @Test void catalogoEsInmutableExactoYNoContieneArte() {
        assertEquals(20, CatalogoAvatarCuidador.ids().size());
        assertThrows(UnsupportedOperationException.class, () -> CatalogoAvatarCuidador.ids().clear());
        assertDoesNotThrow(() -> CatalogoAvatarCuidador.validar(null));
        for (var id : CatalogoAvatarCuidador.ids()) assertDoesNotThrow(() -> CatalogoAvatarCuidador.validar(id));
        for (var invalid : new String[] {"", "person-00", "person-11", "animal-00", "animal-11", "PERSON-01", " person-01", "https://example.com/a.svg"}) {
            assertThrows(IllegalArgumentException.class, () -> CatalogoAvatarCuidador.validar(invalid));
        }
    }

    @Test void cadaAvatarPermitidoSeGuardaConLaCuentaEnElRegistro() {
        for (var id : CatalogoAvatarCuidador.ids()) {
            var f = new Fixture(); var codigo = f.codigo(); codigo.setUsado(true);
            when(f.codigos.findById(7)).thenReturn(Optional.of(codigo));
            when(f.cuidadores.saveAndFlush(any())).thenAnswer(call -> {
                var cuidador = (dia_facil.backend.persistence.entity.Cuidador) call.getArgument(0);
                assertEquals(id, cuidador.getAvatarId()); assertEquals("Grecia", cuidador.getNombre());
                assertEquals(dia_facil.backend.persistence.enums.EstadoCuidador.ACTIVO, cuidador.getEstado());
                org.springframework.test.util.ReflectionTestUtils.setField(cuidador, "idCuidador", 3);
                return cuidador;
            });
            assertEquals(3, f.servicio.completarRegistro(new CriptografiaAutenticacion.Comprobante(EMAIL, 7, NOW.plusSeconds(600)), "Grecia", id));
        }
    }
}
