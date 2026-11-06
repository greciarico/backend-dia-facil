package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.CodigoAcceso;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface CodigoAccesoRepository extends JpaRepository<CodigoAcceso, Integer> {

    // Select the latest issuance even if used or expired; validity belongs to the service.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoAcceso> findFirstByEmailOrderByFechaCreacionDescIdCodigoAccesoDesc(String email);
}
