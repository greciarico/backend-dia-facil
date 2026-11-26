package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.Actividad;
import dia_facil.backend.persistence.enums.EstadoActividad;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.service.actividades.ActividadResumen;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ActividadRepository extends Repository<Actividad, Integer> {
    long countByPerfilUsuario_IdPerfilUsuarioAndEstado(Integer perfil, EstadoActividad estado);

    @Query("""
        select new dia_facil.backend.service.actividades.ActividadDetalle(a.idActividad, coalesce(v.nombre,a.nombre), coalesce(v.hora,a.hora), case when v.id is null then i.codigo else vi.codigo end, e.estado, a.guiaInicialCompletada)
        from Actividad a join a.perfilUsuario p left join a.iconoActividad i
        left join EjecucionActividad e on e.actividad = a and e.fecha = :fecha
        left join VersionActividad v on v.actividad = a and v.numero = e.revision
        left join v.icono vi
        where a.idActividad = :actividad and p.idPerfilUsuario = :perfil
          and p.estado = :estadoPerfil and (a.estado = :estadoActividad or e.idEjecucionActividad is not null)
        """)
    java.util.Optional<dia_facil.backend.service.actividades.ActividadDetalle> detalleUsuario(
        @Param("actividad") Integer actividad, @Param("perfil") Integer perfil,
        @Param("fecha") java.time.LocalDate fecha, @Param("estadoPerfil") EstadoPerfil estadoPerfil,
        @Param("estadoActividad") EstadoActividad estadoActividad);
    @Query("""
        select new dia_facil.backend.service.actividades.ActividadAgenda(a.idActividad, coalesce(v.nombre,a.nombre), coalesce(v.hora,a.hora), case when v.id is null then i.codigo else vi.codigo end, e.estado)
        from Actividad a join a.perfilUsuario p left join a.iconoActividad i
        left join EjecucionActividad e on e.actividad = a and e.fecha = :fecha
        left join VersionActividad v on v.actividad = a and v.numero = e.revision
        left join v.icono vi
        where p.idPerfilUsuario = :perfil and p.estado = :estadoPerfil and (a.estado = :estadoActividad or e.idEjecucionActividad is not null)
        order by coalesce(v.hora,a.hora) asc, a.idActividad asc
        """)
    List<dia_facil.backend.service.actividades.ActividadAgenda> agenda(@Param("perfil") Integer perfil,
        @Param("fecha") java.time.LocalDate fecha, @Param("estadoPerfil") EstadoPerfil estadoPerfil,
        @Param("estadoActividad") EstadoActividad estadoActividad);
    @Query("""
        select new dia_facil.backend.service.actividades.ActividadResumen(a.idActividad, a.nombre, a.hora, i.codigo)
        from Actividad a join a.perfilUsuario p left join a.iconoActividad i
        where p.idPerfilUsuario = :perfil and p.idCuidador = :cuidador
          and p.estado = :estadoPerfil and a.estado = :estadoActividad
        order by a.hora asc, a.idActividad asc
        """)
    List<ActividadResumen> consultar(@Param("perfil") Integer perfil, @Param("cuidador") Integer cuidador,
        @Param("estadoPerfil") EstadoPerfil estadoPerfil, @Param("estadoActividad") EstadoActividad estadoActividad);
}
