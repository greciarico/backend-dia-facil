package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class RegistroSeguimiento {
    record PasoBase(Integer idPasoActividad, int orden, String descripcion, Integer idPictograma, String nombrePictograma, Integer idArasaac) {
        static PasoBase de(PasoActividad paso) {
            var imagen = paso.getPictograma();
            return new PasoBase(paso.getIdPasoActividad(), paso.getOrden(), paso.getDescripcion(), imagen == null ? null : imagen.getIdPictograma(),
                imagen == null ? null : imagen.getNombre(), imagen == null ? null : imagen.getIdArasaac());
        }
        PlantillaActividadService.Pictograma imagen() {
            return idPictograma == null ? null : PlantillaActividadService.Pictograma.de(idPictograma, nombrePictograma, idArasaac);
        }
    }
    record Definicion(int idActividad, int revision, String nombre, LocalTime hora, String codigoIcono, List<PasoBase> pasos) {}
    private final EntityManager em;
    private final Clock reloj;
    private final ObjectMapper json;

    public RegistroSeguimiento(EntityManager em, @Qualifier("agendaUsuarioClock") Clock reloj, ObjectMapper json) {
        this.em = em;
        this.reloj = reloj;
        this.json = json;
    }

    public void guardar(Actividad actividad, List<PasoActividad> pasos) {
        var ordenados = new TreeMap<Integer, PasoBase>();
        for (var paso : pasos) ordenados.put(paso.getIdPasoActividad(), PasoBase.de(paso));
        escribir(actividad, LocalDate.now(reloj), json.writeValueAsString(ordenados), false);
    }

    public void retirar(Actividad actividad, LocalDate fecha) {
        escribir(actividad, fecha, "{}", true);
    }

    private void escribir(Actividad actividad, LocalDate fecha, String pasos, boolean retirada) {
        var icono = actividad.getIconoActividad();
        em.createNativeQuery("""
            insert into seguimiento_rutina
                (id_perfil_usuario,id_actividad,fecha,revision,nombre,hora,codigo_icono,pasos,retirada)
            values (:perfil,:actividad,:fecha,:revision,:nombre,:hora,:icono,:pasos,:retirada)
            on duplicate key update revision=values(revision),nombre=values(nombre),hora=values(hora),
                codigo_icono=values(codigo_icono),pasos=values(pasos),retirada=values(retirada)
            """)
            .setParameter("perfil", actividad.getPerfilUsuario().getIdPerfilUsuario())
            .setParameter("actividad", actividad.getIdActividad()).setParameter("fecha", fecha)
            .setParameter("revision", actividad.getRevision()).setParameter("nombre", actividad.getNombre())
            .setParameter("hora", actividad.getHora()).setParameter("icono", icono == null ? null : icono.getCodigo())
            .setParameter("pasos", pasos).setParameter("retirada", retirada).executeUpdate();
    }

    LocalDate inicio() {
        var fecha = em.createNativeQuery("select fecha_inicio from seguimiento_cobertura where id = 1", LocalDate.class).getSingleResult();
        return (LocalDate) fecha;
    }

    boolean tieneRutinaHistorica(int perfil, LocalDate hoy) {
        return !em.createNativeQuery("select 1 from seguimiento_rutina where id_perfil_usuario = :perfil and fecha < :hoy and retirada = 0")
            .setParameter("perfil", perfil).setParameter("hoy", hoy).setMaxResults(1).getResultList().isEmpty();
    }

    List<Definicion> delDia(int perfil, LocalDate fecha) {
        var filas = em.createNativeQuery("""
            select r.id_actividad,r.revision,r.nombre,date_format(r.hora,'%H:%i:%s'),r.codigo_icono,cast(r.pasos as char)
            from seguimiento_rutina r
            where r.id_perfil_usuario = :perfil and r.fecha <= :fecha and r.retirada = 0
              and not exists (select 1 from seguimiento_rutina n
                where n.id_perfil_usuario = r.id_perfil_usuario and n.id_actividad = r.id_actividad
                  and n.fecha > r.fecha and n.fecha <= :fecha)
            """).setParameter("perfil", perfil).setParameter("fecha", fecha).getResultList();
        var resultado = new ArrayList<Definicion>();
        for (Object fila : filas) {
            var r = (Object[]) fila;
            var hora = LocalTime.parse((String) r[3]);
            TreeMap<Integer, PasoBase> pasos = json.readValue((String) r[5], new TypeReference<>() {});
            resultado.add(new Definicion(((Number) r[0]).intValue(), ((Number) r[1]).intValue(), (String) r[2],
                hora, (String) r[4], pasos.values().stream().sorted(Comparator.comparingInt(PasoBase::orden)
                    .thenComparingInt(PasoBase::idPasoActividad)).toList()));
        }
        return resultado;
    }
}
