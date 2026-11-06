package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.PlantillaActividad;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantillaActividadRepository extends JpaRepository<PlantillaActividad, Integer> {
    interface Candidata {
        Integer getId();
        String getNombre();
        String getPalabra();
    }
    interface Paso {
        Integer getOrden();
        String getDescripcion();
        Integer getIdPictograma();
        String getNombrePictograma();
        Integer getIdArasaac();
    }
    @Query("""
        select t.idPlantillaActividad as id, t.nombre as nombre, p.palabra as palabra
        from PlantillaActividad t left join PalabraClaveActividad p on p.plantillaActividad = t
        """)
    List<Candidata> candidatas();

    @Query("""
        select p.orden as orden, p.descripcion as descripcion,
            i.idPictograma as idPictograma, i.nombre as nombrePictograma, i.idArasaac as idArasaac
        from PlantillaPaso p left join p.pictograma i
        where p.plantillaActividad.idPlantillaActividad = :plantilla order by p.orden
        """)
    List<Paso> pasos(@Param("plantilla") Integer plantilla);
}
