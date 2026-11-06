package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.EstadoDispositivo;
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
@Table(name = "dispositivo", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", indexes = {
    @Index(name = "idx_dispositivo_perfil_estado", columnList = "id_perfil_usuario,estado")
})
public class Dispositivo {
    public static Dispositivo vincular(PerfilUsuario perfil, String nombre, LocalDateTime ahora) {
        var dispositivo = new Dispositivo(); dispositivo.perfilUsuario = perfil; dispositivo.nombre = nombre;
        dispositivo.estado = EstadoDispositivo.ACTIVO; dispositivo.fechaUltimaActividad = ahora; return dispositivo;
    }

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dispositivo", nullable = false)
    private Integer idDispositivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_perfil_usuario", nullable = false, foreignKey = @ForeignKey(name = "fk_dispositivo_perfil"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PerfilUsuario perfilUsuario;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado", nullable = false)
    private EstadoDispositivo estado = EstadoDispositivo.ACTIVO;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_vinculacion", nullable = false, secondPrecision = 0, insertable = false, updatable = false)
    private LocalDateTime fechaVinculacion;

    @Column(name = "fecha_ultima_actividad", nullable = true, secondPrecision = 0)
    private LocalDateTime fechaUltimaActividad;

    @Column(name = "fecha_desvinculacion", nullable = true, secondPrecision = 0)
    private LocalDateTime fechaDesvinculacion;
}

