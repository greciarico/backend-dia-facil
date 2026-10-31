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
@Table(name = "plantilla_paso", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_plantilla_paso_orden", columnNames = {"id_plantilla_actividad", "orden"})
})
public class PlantillaPaso {
    public static PlantillaPaso crear(PlantillaActividad plantilla, Integer orden, String descripcion, Pictograma pictograma) {
        var paso = new PlantillaPaso(); paso.plantillaActividad = plantilla; paso.orden = orden;
        paso.descripcion = descripcion; paso.pictograma = pictograma; return paso;
    }


    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plantilla_paso", nullable = false)
    private Integer idPlantillaPaso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_plantilla_actividad", nullable = false, foreignKey = @ForeignKey(name = "fk_plantilla_paso_plantilla"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlantillaActividad plantillaActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_pictograma", nullable = true, foreignKey = @ForeignKey(name = "fk_plantilla_paso_pictograma"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Pictograma pictograma;

    @Column(name = "orden", nullable = false, columnDefinition = "smallint unsigned")
    private Integer orden;

    @Column(name = "descripcion", nullable = false, length = 120)
    private String descripcion;
}

