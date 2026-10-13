package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.SesionCuidador;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface SesionCuidadorRepository extends JpaRepository<SesionCuidador, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionCuidador s join fetch s.cuidador where s.idSesion = :id")
    Optional<SesionCuidador> bloquear(Long id);
    @Query("select s.idSesion from SesionCuidador s where s.venceEn < :limite or s.inactividadHasta < :limite or s.revocadaEn < :limite")
    List<Long> finalizadasAntes(LocalDateTime limite);
}
