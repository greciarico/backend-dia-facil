package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.ActividadRepository;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService;
import dia_facil.backend.service.perfiles.PerfilResumen;
import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name="diafacil.dispositivos.enabled",havingValue="true")
public class AgendaUsuarioService {
    private final AccesoDispositivoService acceso;
    private final ActividadRepository actividades;
    private final Clock clock;
    public AgendaUsuarioService(AccesoDispositivoService acceso, ActividadRepository actividades, @Qualifier("agendaUsuarioClock") Clock clock) {
        this.acceso=acceso;this.actividades=actividades;this.clock=clock;
    }
    public record Agenda(LocalDate fecha, String zonaHoraria, long actualizarEnSegundos, PerfilResumen perfil, List<ActividadAgenda> actividades) {}
    public record Detalle(LocalDate fecha, String zonaHoraria, long actualizarEnSegundos, ActividadDetalle actividad) {}
    private record Dia(LocalDate fecha, String zonaHoraria, long actualizarEnSegundos) {}
    @Transactional(readOnly=true)
    public Agenda consultar(AccesoDispositivoService.Identidad principal) {
        var perfil=acceso.perfilActivo(principal);
        var dia=diaActual();
        return new Agenda(dia.fecha(),dia.zonaHoraria(),dia.actualizarEnSegundos(),perfil,
            List.copyOf(actividades.agenda(perfil.idPerfilUsuario(),dia.fecha(),EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA)));
    }
    @Transactional(readOnly=true)
    public Detalle detalle(AccesoDispositivoService.Identidad principal, Integer idActividad) {
        var perfil=acceso.perfilActivo(principal);
        if(idActividad==null || idActividad<=0) throw RechazoActividad.noEncontrada();
        var dia=diaActual();
        var actividad=actividades.detalleUsuario(idActividad,perfil.idPerfilUsuario(),dia.fecha(),EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA)
            .orElseThrow(RechazoActividad::noEncontrada);
        return new Detalle(dia.fecha(),dia.zonaHoraria(),dia.actualizarEnSegundos(),actividad);
    }
    private Dia diaActual() {
        var now=clock.instant();var fecha=now.atZone(clock.getZone()).toLocalDate();
        long remaining=Math.max(1,(Duration.between(now,fecha.plusDays(1).atStartOfDay(clock.getZone()).toInstant()).toMillis()+999)/1000);
        return new Dia(fecha,clock.getZone().getId(),remaining);
    }
}
