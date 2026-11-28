package dia_facil.backend.service.auth;

import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.repository.CuidadorRepository;
import dia_facil.backend.service.fotos.FotosPerfilService;
import dia_facil.backend.service.perfiles.EditarPerfilService.Entrada;
import dia_facil.backend.service.perfiles.RechazoPerfil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static dia_facil.backend.service.perfiles.EditarPerfilService.Imagen.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DatosPersonalesServiceTest {
    @Test void conservaCorreoYRetiraFotoSoloCuandoCambiaLaImagen() {
        var repo = mock(CuidadorRepository.class);
        var tx = mock(PlatformTransactionManager.class);
        var em = mock(EntityManager.class);
        var fotos = mock(FotosPerfilService.class);
        var service = new DatosPersonalesService(repo, fotos, em, tx, "dev");
        var cuenta = Cuidador.crear("cuidador@example.test", "Antes");
        cuenta.setFotoReferencia("anterior");
        when(repo.bloquearParaCrearPerfil(1)).thenReturn(Optional.of(cuenta));
        when(repo.findById(1)).thenReturn(Optional.of(cuenta));
        when(tx.getTransaction(any())).thenAnswer(invocacion -> new SimpleTransactionStatus());

        var resultado = service.editar(1, new Entrada(1, "Nueva", CONSERVAR, null), null);
        assertEquals("cuidador@example.test", resultado.correo());
        assertEquals(2, resultado.cuidador().revision());
        assertEquals("anterior", cuenta.getFotoReferencia());
        verify(em, never()).createNativeQuery(anyString());

        var error = assertThrows(RechazoPerfil.class, () -> service.editar(1, new Entrada(1, "Otra", FOTO, null),
            new MockMultipartFile("foto", new byte[]{1})));
        assertEquals("PERFIL_MODIFICADO", error.codigo());
        verifyNoInteractions(fotos);

        var retirada = mock(Query.class, RETURNS_SELF);
        when(em.createNativeQuery(contains("insert into foto_perfil_operacion"))).thenReturn(retirada);
        assertEquals(3, service.editar(1, new Entrada(2, "Nueva", INICIAL, null), null).cuidador().revision());
        assertNull(cuenta.getFotoReferencia());
        verify(retirada).setParameter("ref", "anterior");
        verify(retirada).setParameter("destino", "CUIDADORES");
        verify(retirada).executeUpdate();

        assertEquals("PERFIL_NO_ENCONTRADO", assertThrows(RechazoPerfil.class, () -> service.consultar(2)).codigo());
    }
}
