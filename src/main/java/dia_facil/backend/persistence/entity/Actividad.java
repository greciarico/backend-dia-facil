package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.EstadoActividad;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "actividad", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", indexes = {
    @Index(name = "idx_actividad_agenda", columnList = "id_perfil_usuario,estado,hora")
})
public class Actividad {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_actividad", nullable = false)
    private Integer idActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_perfil_usuario", nullable = false, foreignKey = @ForeignKey(name = "fk_actividad_perfil"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PerfilUsuario perfilUsuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_pictograma", nullable = true, foreignKey = @ForeignKey(name = "fk_actividad_pictograma"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Pictograma pictograma;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "hora", nullable = false, secondPrecision = 0)
    private LocalTime hora;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @ColumnDefault("0")
    @Column(name = "guia_inicial_completada", nullable = false)
    private Boolean guiaInicialCompletada = false;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVA'")
    @Column(name = "estado", nullable = false)
    private EstadoActividad estado = EstadoActividad.ACTIVA;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_creacion", nullable = false, secondPrecision = 0, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}

