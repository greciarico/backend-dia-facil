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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "paso_actividad", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", indexes = {
    @Index(name = "idx_paso_actividad_orden", columnList = "id_actividad,orden")
})
public class PasoActividad {
    public static PasoActividad crear(Actividad actividad, Integer orden, String descripcion, Pictograma pictograma) {
        var paso = new PasoActividad(); paso.actividad = actividad; paso.orden = orden;
        paso.descripcion = descripcion; paso.pictograma = pictograma; return paso;
    }

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paso_actividad", nullable = false)
    private Integer idPasoActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_actividad", nullable = false, foreignKey = @ForeignKey(name = "fk_paso_actividad"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Actividad actividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_pictograma", nullable = true, foreignKey = @ForeignKey(name = "fk_paso_pictograma"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Pictograma pictograma;

    @Column(name = "orden", nullable = false, columnDefinition = "smallint unsigned")
    private Integer orden;

    @Column(name = "descripcion", nullable = false, length = 120)
    private String descripcion;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}

