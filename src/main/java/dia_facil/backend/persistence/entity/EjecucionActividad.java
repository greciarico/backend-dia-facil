package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.EstadoEjecucion;
import dia_facil.backend.persistence.enums.ModoFinalizacion;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.generator.EventType;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ejecucion_actividad", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_ejecucion_actividad_fecha", columnNames = {"id_actividad", "fecha"})
}, indexes = {
    @Index(name = "idx_ejecucion_fecha", columnList = "fecha")
}, check = {
    @CheckConstraint(name = "chk_ejecucion_cierre", constraint = "( estado = 'COMPLETADA' AND modo_finalizacion IS NOT NULL AND fecha_fin IS NOT NULL ) OR ( estado = 'EN_PROGRESO' AND modo_finalizacion IS NULL AND fecha_fin IS NULL )")
})
public class EjecucionActividad {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ejecucion_actividad", nullable = false)
    private Integer idEjecucionActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_actividad", nullable = false, foreignKey = @ForeignKey(name = "fk_ejecucion_actividad"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Actividad actividad;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'EN_PROGRESO'")
    @Column(name = "estado", nullable = false)
    private EstadoEjecucion estado = EstadoEjecucion.EN_PROGRESO;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_finalizacion", nullable = true)
    private ModoFinalizacion modoFinalizacion;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_inicio", nullable = false, secondPrecision = 0, insertable = false, updatable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = true, secondPrecision = 0)
    private LocalDateTime fechaFin;
}

