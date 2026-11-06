package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.CredencialSesionCuidador;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface CredencialSesionCuidadorRepository extends JpaRepository<CredencialSesionCuidador, Long> {
    @Query("select c from CredencialSesionCuidador c join fetch c.sesion s join fetch s.cuidador where c.accessHash = :hash")
    Optional<CredencialSesionCuidador> findByAccessHash(byte[] hash);
    Optional<CredencialSesionCuidador> findByRefreshHash(byte[] hash);
    Optional<CredencialSesionCuidador> findFirstBySesionIdSesionOrderByGeneracionDesc(Long idSesion);
    void deleteBySesionIdSesion(Long idSesion);
}
