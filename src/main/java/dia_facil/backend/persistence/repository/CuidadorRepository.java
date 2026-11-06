package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.Cuidador;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuidadorRepository extends JpaRepository<Cuidador, Integer> {

    Optional<Cuidador> findByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cuidador c where c.idCuidador = :id")
    Optional<Cuidador> bloquearParaCrearPerfil(@org.springframework.data.repository.query.Param("id") Integer id);
}
