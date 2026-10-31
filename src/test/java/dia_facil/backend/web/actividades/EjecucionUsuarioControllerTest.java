package dia_facil.backend.web.actividades;
import dia_facil.backend.configuration.*;
import dia_facil.backend.service.actividades.*;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import java.time.LocalDate;
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
@WebMvcTest(controllers=EjecucionUsuarioController.class,properties="diafacil.dispositivos.enabled=true")
@Import({SecurityConfiguration.class,DispositivoSecurityConfiguration.class,AutenticacionExceptionHandler.class})
class EjecucionUsuarioControllerTest {
 @Autowired MockMvc mvc;@MockitoBean AccesoDispositivoService acceso;@MockitoBean EjecucionUsuarioService service;
 static final String TOKEN="du_"+"D".repeat(43),BASE="/api/usuario/actividades/7";
 final AccesoDispositivoService.Identidad principal=new AccesoDispositivoService.Identidad(1L,12);
 final LocalDate date=LocalDate.of(2026,10,24);
 final EjecucionUsuarioService.ActividadInfo activity=new EjecucionUsuarioService.ActividadInfo(7,"Actividad",false);
 EjecucionUsuarioService.Guia guide(){return new EjecucionUsuarioService.Guia(date,"America/Lima",60,true,3,"EN_PROGRESO",null,activity,List.of(new EjecucionUsuarioService.Paso(1,1,"Paso",null,true,false)));}
 void auth(){when(acceso.autenticar(TOKEN)).thenReturn(principal);}
 @Test void rutasDeLecturaNoCreanYUsanSoloPrincipal() throws Exception {
  auth();when(service.consultar(principal,7,null)).thenReturn(guide());when(service.consultar(principal,7,3)).thenReturn(guide());
  mvc.perform(get(BASE+"/guia").param("idPerfilUsuario","999").header("Authorization","Bearer "+TOKEN)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.pasos[0].alcanzado").value(true));
  mvc.perform(get(BASE+"/ejecuciones/3").header("Authorization","Bearer "+TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.idEjecucion").value(3));
  verify(service).consultar(principal,7,null);verify(service).consultar(principal,7,3);verify(service,never()).iniciar(any(),any(),any());
 }
 @Test void comandosSinCookiesNiCsrfUsanPrincipalYRespuestasNoCacheables() throws Exception {
  auth();when(service.iniciar(principal,7,date)).thenReturn(guide());when(service.avanzar(principal,7,3,1)).thenReturn(guide());
  var done=new EjecucionUsuarioService.Finalizacion(date,3,"COMPLETADA","GUIADA",new EjecucionUsuarioService.ActividadInfo(7,"Actividad",true));
  when(service.finalizarGuiada(principal,7,3,1)).thenReturn(done);when(service.finalizarDirecta(principal,7,date)).thenReturn(done);
  mvc.perform(post(BASE+"/guia").header("Authorization","Bearer "+TOKEN).contentType("application/json").content("{\"fecha\":\"2026-10-24\",\"idPerfilUsuario\":999}"))
    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(put(BASE+"/ejecuciones/3/pasos/1/completar").header("Authorization","Bearer "+TOKEN)).andExpect(status().isOk());
  mvc.perform(post(BASE+"/ejecuciones/3/finalizar-guiada").header("Authorization","Bearer "+TOKEN).contentType("application/json").content("{\"idPasoActual\":1}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPLETADA")).andExpect(jsonPath("$.pasos").doesNotExist());
  mvc.perform(post(BASE+"/finalizar-directa").header("Authorization","Bearer "+TOKEN).contentType("application/json").content("{\"fecha\":\"2026-10-24\"}"))
    .andExpect(status().isOk());
  verify(service).iniciar(principal,7,date);verify(service).avanzar(principal,7,3,1);verify(service).finalizarGuiada(principal,7,3,1);verify(service).finalizarDirecta(principal,7,date);
 }
 @Test void erroresDeNegocioNoSeConviertenEnErrorTecnico() throws Exception {
  auth();when(service.iniciar(principal,7,date)).thenThrow(RechazoGuia.dia());when(service.finalizarDirecta(principal,7,date)).thenThrow(RechazoGuia.inicial());
  mvc.perform(post(BASE+"/guia").header("Authorization","Bearer "+TOKEN).contentType("application/json").content("{\"fecha\":\"2026-10-24\"}"))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DIA_AGENDA_CAMBIADO"));
  mvc.perform(post(BASE+"/finalizar-directa").header("Authorization","Bearer "+TOKEN).contentType("application/json").content("{\"fecha\":\"2026-10-24\"}"))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("GUIA_INICIAL_REQUERIDA"));
  mvc.perform(post(BASE+"/guia").header("Authorization","Bearer "+TOKEN).contentType("application/json").content("{}"))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
 }
 @Test void pendientesAnonimosYCuidadorNoPuedenEjecutar() throws Exception {
  when(acceso.autenticar(TOKEN)).thenReturn(new AccesoDispositivoService.Identidad(1L,null));
  mvc.perform(post(BASE+"/guia").header("Authorization","Bearer "+TOKEN).contentType("application/json").content("{\"fecha\":\"2026-10-24\"}")).andExpect(status().isForbidden());
  mvc.perform(put(BASE+"/ejecuciones/3/pasos/1/completar").header("Authorization","Bearer "+TOKEN)).andExpect(status().isForbidden());
  mvc.perform(get(BASE+"/guia")).andExpect(status().isUnauthorized());mvc.perform(get(BASE+"/guia").header("Authorization","Bearer a_"+"A".repeat(43))).andExpect(status().isUnauthorized());
  verifyNoInteractions(service);
 }
}
