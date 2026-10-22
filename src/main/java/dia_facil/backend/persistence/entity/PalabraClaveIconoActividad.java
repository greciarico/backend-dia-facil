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
@Table(name = "palabra_clave_icono_actividad", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_palabra_icono_actividad", columnNames = {"id_icono_actividad", "palabra"})
}, indexes = { @Index(name = "idx_palabra_icono_actividad", columnList = "palabra") })
public class PalabraClaveIconoActividad {
    public static PalabraClaveIconoActividad crear(IconoActividad icono, String palabra) {
        var keyword = new PalabraClaveIconoActividad(); keyword.iconoActividad = icono;
        keyword.palabra = palabra; return keyword;
    }

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_palabra_clave_icono_actividad", nullable = false)
    private Integer idPalabraClaveIconoActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_icono_actividad", nullable = false, foreignKey = @ForeignKey(name = "fk_palabra_icono_actividad"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private IconoActividad iconoActividad;

    @Column(name = "palabra", nullable = false, length = 60)
    private String palabra;
}
