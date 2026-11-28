package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.PerfilUsuarioRepository;
import dia_facil.backend.service.fotos.FotosPerfilService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static dia_facil.backend.service.perfiles.EditarPerfilService.Imagen.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EditarPerfilServiceTest {
    private final PerfilUsuarioRepository perfiles = mock(PerfilUsuarioRepository.class);
    private final FotosPerfilService fotos = mock(FotosPerfilService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final EditarPerfilService servicio = new EditarPerfilService(perfiles, fotos, em, tx, "dev");
    private final PerfilUsuario perfil = PerfilUsuario.crear(Cuidador.crear("ana@example.test", "Ana"), "Anterior", null, "codigo");

    @Test void nombreConservaFotoYNoopNoIncrementaRevision() {
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(perfiles.bloquear(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        perfil.setFotoReferencia("foto-original");
        var resultado = servicio.editar(3, 12, new EditarPerfilService.Entrada(1, "Nuevo", CONSERVAR, null), null);
        assertEquals(2, resultado.revision());
        assertEquals("foto-original", perfil.getFotoReferencia());
        assertEquals(2, servicio.editar(3, 12, new EditarPerfilService.Entrada(2, "Nuevo", CONSERVAR, null), null).revision());
        verifyNoInteractions(fotos);
        verify(em, never()).createNativeQuery(anyString());
    }

    @Test void protegeFotoAntesDeSubirYRetiraLaAnteriorAlGuardarInicial() {
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(perfiles.bloquear(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        when(perfiles.findByIdPerfilUsuarioAndIdCuidadorAndEstado(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        perfil.setFotoReferencia("foto-original");
        perfil.setRevisionPerfil(2);
        var archivo = new MockMultipartFile("foto", new byte[]{1});
        var rechazo = assertThrows(RechazoPerfil.class,
            () -> servicio.editar(3, 12, new EditarPerfilService.Entrada(1, "Nuevo", FOTO, null), archivo));
        assertEquals("PERFIL_MODIFICADO", rechazo.codigo());
        assertEquals("foto-original", perfil.getFotoReferencia());
        verifyNoInteractions(fotos);
        var cola = mock(Query.class, RETURNS_SELF);
        when(em.createNativeQuery(contains("insert into foto_perfil_operacion"))).thenReturn(cola);
        var resultado = servicio.editar(3, 12, new EditarPerfilService.Entrada(2, "Nuevo", INICIAL, null), null);
        assertEquals(3, resultado.revision());
        assertNull(perfil.getFotoReferencia());
        verify(cola).setParameter("ref", "foto-original");
        verify(cola).executeUpdate();
        verifyNoInteractions(fotos);
    }
}
