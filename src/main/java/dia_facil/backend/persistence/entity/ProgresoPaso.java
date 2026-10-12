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
import java.time.LocalDateTime;
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
@Table(name = "progreso_paso", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_progreso_ejecucion_paso", columnNames = {"id_ejecucion_actividad", "id_paso_actividad"})
}, indexes = {
    @Index(name = "idx_progreso_paso", columnList = "id_paso_actividad")
})
public class ProgresoPaso {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_progreso_paso", nullable = false)
    private Integer idProgresoPaso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ejecucion_actividad", nullable = false, foreignKey = @ForeignKey(name = "fk_progreso_ejecucion"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private EjecucionActividad ejecucionActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paso_actividad", nullable = false, foreignKey = @ForeignKey(name = "fk_progreso_paso"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PasoActividad pasoActividad;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @ColumnDefault("0")
    @Column(name = "completado", nullable = false)
    private Boolean completado = false;

    @Column(name = "fecha_completado", nullable = true, secondPrecision = 0)
    private LocalDateTime fechaCompletado;
}

