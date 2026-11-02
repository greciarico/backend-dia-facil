package dia_facil.backend.service.notificaciones;
import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.service.auth.SesionCuidadorService.*;
import dia_facil.backend.service.ayuda.RechazoAyuda;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NotificacionCuidadorServiceTest {
 final EntityManager em=mock(EntityManager.class);final NotificacionCuidadorService service=new NotificacionCuidadorService(em);
 final Identidad principal=new Identidad(1L,new CuidadorResumen(4,"Test",null));
 final List<NotificacionCuidador> rows=new ArrayList<>();final Map<String,Object> params=new HashMap<>();
 @BeforeEach @SuppressWarnings("unchecked") void setup(){
  var p=PerfilUsuario.crear(Cuidador.crear("test@example.invalid","Test"),"Grecia",null,"DF-TEST");
  for(int i=21;i>0;i--){var n=NotificacionCuidador.ayuda(SolicitudAyuda.crear(p,"clave",null,null,null,null));ReflectionTestUtils.setField(n,"idNotificacionCuidador",i);ReflectionTestUtils.setField(n,"fechaCreacion",LocalDateTime.of(2026,11,1,7,0));rows.add(n);}
  when(em.createQuery(anyString(),eq(NotificacionCuidador.class))).thenAnswer(call->{var q=mock(TypedQuery.class);when(q.setParameter(anyString(),any())).thenAnswer(c->{params.put(c.getArgument(0),c.getArgument(1));return q;});when(q.setMaxResults(anyInt())).thenReturn(q);when(q.getResultList()).thenReturn(rows);when(q.getResultStream()).thenAnswer(c->rows.stream().limit(1));return q;});
 }
 @Test void paginaYCursorPorFechaEId(){var r=service.listar(principal,"TODAS",null);assertEquals(20,r.notificaciones().size());assertNotNull(r.siguienteCursor());service.listar(principal,"TODAS",r.siguienteCursor());assertEquals(2,params.get("id"));assertEquals(4,params.get("cuidador"));}
 @Test void cursorLigadoACategoriaYPropietario(){var c=service.listar(principal,"AYUDA",null).siguienteCursor();assertThrows(RechazoAyuda.class,()->service.listar(principal,"SISTEMA",c));assertThrows(RechazoAyuda.class,()->service.listar(new Identidad(2L,new CuidadorResumen(99,"Otra",null)),"AYUDA",c));assertThrows(RechazoAyuda.class,()->service.listar(principal,"TODAS","basura"));}
 @Test void filtrosYMarcadoIdempotente(){service.listar(principal,"AYUDA",null);assertTrue(params.containsKey("general"));assertTrue(service.leer(principal,21).leida());assertTrue(service.leer(principal,21).leida());verify(em,never()).persist(any());}
 @Test void categoriaInvalidaYSinSesion(){assertThrows(RechazoAyuda.class,()->service.listar(principal,"OTRA",null));assertEquals(401,assertThrows(RechazoAyuda.class,()->service.listar(null,"TODAS",null)).status());}
}
