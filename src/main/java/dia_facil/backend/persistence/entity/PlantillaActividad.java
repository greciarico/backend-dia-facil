package dia_facil.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "plantilla_actividad", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_plantilla_nombre", columnNames = {"nombre"})
})
public class PlantillaActividad {
    public static PlantillaActividad crear(String nombre, IconoActividad icono) {
        var plantilla = new PlantillaActividad(); plantilla.nombre = nombre;
        plantilla.iconoActividad = icono; return plantilla;
    }


    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plantilla_actividad", nullable = false)
    private Integer idPlantillaActividad;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_icono_actividad", nullable = true, foreignKey = @ForeignKey(name = "fk_plantilla_icono"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private IconoActividad iconoActividad;
}

