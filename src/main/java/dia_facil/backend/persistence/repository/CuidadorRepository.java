package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.Cuidador;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuidadorRepository extends JpaRepository<Cuidador, Integer> {

    Optional<Cuidador> findByEmail(String email);
}
