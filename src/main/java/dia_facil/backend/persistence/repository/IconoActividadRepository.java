package dia_facil.backend.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import dia_facil.backend.persistence.entity.IconoActividad;

public interface IconoActividadRepository extends JpaRepository<IconoActividad, Integer> {
    java.util.Optional<IconoActividad> findByCodigo(String codigo);
    interface Fila {
        String getCodigo();
        String getNombre();
        String getPalabra();
    }

    @Query("""
        select i.codigo as codigo, i.nombre as nombre, p.palabra as palabra
        from IconoActividad i left join PalabraClaveIconoActividad p on p.iconoActividad = i
        order by i.codigo
        """)
    List<Fila> consultarCatalogo();
}
