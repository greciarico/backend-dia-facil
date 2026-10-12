package dia_facil.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "palabra_clave_actividad", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_palabra_actividad", columnNames = {"id_plantilla_actividad", "palabra"})
}, indexes = {
    @Index(name = "idx_palabra_actividad", columnList = "palabra")
})
public class PalabraClaveActividad {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_palabra_clave_actividad", nullable = false)
    private Integer idPalabraClaveActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_plantilla_actividad", nullable = false, foreignKey = @ForeignKey(name = "fk_palabra_actividad_plantilla"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlantillaActividad plantillaActividad;

    @Column(name = "palabra", nullable = false, length = 60)
    private String palabra;
}

