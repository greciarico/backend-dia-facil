package dia_facil.backend.service.push;
import dia_facil.backend.web.push.RegistroPushController;
import dia_facil.backend.service.auth.SesionCuidadorService.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistroPushControllerTest {
    private static final UUID ID = UUID.randomUUID();
    private static final String TAG = RegistroPushService.etag(ID, 1L, 0);
    @Test void contratoGetPutDeleteConEtagNoFiltraTokens() {
        var svc = mock(RegistroPushService.class); var c = new RegistroPushController(svc);
        var p = new Identidad(1L, new CuidadorResumen(2,"C",null));
        var dto = new RegistroPushService.Estado(true,3,1L,2,"project");
        var result = new RegistroPushService.Versionado(dto,TAG);
        var body = new RegistroPushService.Peticion(ID,"ExpoPushToken[abc]","ANDROID");
        when(svc.registrar(p,body,TAG)).thenReturn(result); when(svc.consultar(p,ID)).thenReturn(result);
        when(svc.eliminar(p,ID,TAG)).thenReturn(TAG);
        assertEquals(dto,c.put(p,body,TAG).getBody());
        assertEquals(dto,c.get(p,ID).getBody());
        assertEquals(TAG,c.get(p,ID).getHeaders().getETag());
        assertEquals(TAG,c.put(p,body,TAG).getHeaders().getETag());
        assertEquals("no-store",c.get(p,ID).getHeaders().getCacheControl());
        assertEquals(204,c.delete(p,ID,TAG).getStatusCode().value());
        assertEquals(TAG,c.delete(p,ID,TAG).getHeaders().getETag());
    }
    @Test void reintentaConLaMismaPrecondicionEnTransaccionNueva() {
        var svc=mock(RegistroPushService.class); var c=new RegistroPushController(svc);
        var body=new RegistroPushService.Peticion(ID,"ExpoPushToken[abc]","ANDROID");
        when(svc.registrar(null,body,TAG)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("conflict"))
            .thenReturn(new RegistroPushService.Versionado(new RegistroPushService.Estado(true,1,1L,2,"p"),TAG));
        assertEquals(200,c.put(null,body,TAG).getStatusCode().value());
        verify(svc,times(2)).registrar(null,body,TAG);
    }
    @Test void agotamientoConcurrenteEs409No500() {
        var svc=mock(RegistroPushService.class); var c=new RegistroPushController(svc);
        when(svc.registrar(null,null,TAG)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private database detail"));
        var ex=assertThrows(ResponseStatusException.class,()->c.put(null,null,TAG));
        assertEquals(409,ex.getStatusCode().value()); assertFalse(ex.getReason().contains("private"));
    }
    @Test void faltaPrecondicionEs428YFormatoInvalidoEs400SinTocarPersistencia() {
        var em=mock(EntityManager.class); var svc=new RegistroPushService(em,new PushProperties(true,"project",null));
        assertEquals(428,assertThrows(ResponseStatusException.class,()->svc.registrar(null,null,null)).getStatusCode().value());
        assertEquals(428,assertThrows(ResponseStatusException.class,()->svc.eliminar(null,ID,null)).getStatusCode().value());
        for (var tag: new String[]{"*", "W/"+TAG, TAG+","+TAG, "", "0"}) {
            assertEquals(400,assertThrows(ResponseStatusException.class,()->svc.eliminar(null,ID,tag)).getStatusCode().value());
        }
        verifyNoInteractions(em);
    }
    @Test void conflictoDePrecondicionNoSeReintentaNiActualizaSolo() {
        var svc=mock(RegistroPushService.class); var c=new RegistroPushController(svc);
        when(svc.eliminar(null,ID,TAG)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.PRECONDITION_FAILED));
        assertEquals(412,assertThrows(ResponseStatusException.class,()->c.delete(null,ID,TAG)).getStatusCode().value());
        verify(svc).eliminar(null,ID,TAG);
    }
    @Test void etagEsEstableOpacoYDependeDeInstalacionSesionYVersion() {
        assertTrue(TAG.matches("\"[0-9a-f]{64}\""));
        assertEquals(TAG,RegistroPushService.etag(ID,1L,0));
        assertNotEquals(TAG,RegistroPushService.etag(ID,2L,0));
        assertNotEquals(TAG,RegistroPushService.etag(ID,1L,1));
        assertNotEquals(TAG,RegistroPushService.etag(UUID.randomUUID(),1L,0));
    }
}
