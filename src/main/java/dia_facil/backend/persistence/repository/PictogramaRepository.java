package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.Pictograma;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface PictogramaRepository extends Repository<Pictograma, Integer> {
    List<Pictograma> findByIdArasaacIn(Collection<Integer> ids);
}
