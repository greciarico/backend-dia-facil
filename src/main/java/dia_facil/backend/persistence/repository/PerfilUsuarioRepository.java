package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.service.perfiles.PerfilResumen;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PerfilUsuarioRepository extends Repository<PerfilUsuario, Integer> {
    java.util.Optional<PerfilUsuario> findByIdPerfilUsuarioAndIdCuidadorAndEstado(Integer perfil, Integer cuidador, EstadoPerfil estado);
    @Query("""
        select new dia_facil.backend.service.perfiles.PerfilResumen(p.idPerfilUsuario, p.nombre, p.avatar)
        from PerfilUsuario p
        where p.idPerfilUsuario = :perfil and p.idCuidador = :cuidador and p.estado = :estado
        """)
    java.util.Optional<PerfilResumen> detalle(@Param("perfil") Integer perfil, @Param("cuidador") Integer cuidador,
        @Param("estado") EstadoPerfil estado);

    long countByIdCuidadorAndEstado(Integer idCuidador, EstadoPerfil estado);

    @Query("""
        select new dia_facil.backend.service.perfiles.PerfilResumen(p.idPerfilUsuario, p.nombre, p.avatar)
        from PerfilUsuario p
        where p.idCuidador = :cuidador and p.estado = :estado
        order by p.nombre asc, p.idPerfilUsuario asc
        """)
    List<PerfilResumen> consultar(@Param("cuidador") Integer cuidador, @Param("estado") EstadoPerfil estado);
}
