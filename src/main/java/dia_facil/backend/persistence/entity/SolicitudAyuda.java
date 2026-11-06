package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.EstadoSolicitudAyuda;
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
@Table(name = "solicitud_ayuda", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = @jakarta.persistence.UniqueConstraint(name="uq_ayuda_perfil_operacion", columnNames={"id_perfil_usuario","clave_operacion"}), indexes = {
    @Index(name="idx_ayuda_contexto",columnList="id_perfil_usuario,id_ejecucion_actividad,id_paso_actividad,fecha_solicitud"),
    @Index(name = "idx_ayuda_perfil_estado", columnList = "id_perfil_usuario,estado,fecha_solicitud"),
    @Index(name = "idx_ayuda_actividad", columnList = "id_actividad,fecha_solicitud")
})
public class SolicitudAyuda {
    public static SolicitudAyuda crear(PerfilUsuario perfil, String clave, Actividad actividad, EjecucionActividad ejecucion, PasoActividad paso, Integer posicion) {
        var s=new SolicitudAyuda(); s.perfilUsuario=perfil; s.claveOperacion=clave; s.actividad=actividad;
        s.ejecucionActividad=ejecucion; s.pasoActividad=paso; s.posicionPaso=posicion;
        s.descripcionPaso=paso==null?null:paso.getDescripcion(); return s;
    }
    @Column(name="clave_operacion", length=36) private String claveOperacion;
    @Column(name="posicion_paso") private Integer posicionPaso;
    @Column(name="descripcion_paso", length=120) private String descripcionPaso;


    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_ayuda", nullable = false)
    private Integer idSolicitudAyuda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_perfil_usuario", nullable = false, foreignKey = @ForeignKey(name = "fk_ayuda_perfil"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PerfilUsuario perfilUsuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_actividad", nullable = true, foreignKey = @ForeignKey(name = "fk_ayuda_actividad"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Actividad actividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_ejecucion_actividad", nullable = true, foreignKey = @ForeignKey(name = "fk_ayuda_ejecucion"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private EjecucionActividad ejecucionActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_paso_actividad", nullable = true, foreignKey = @ForeignKey(name = "fk_ayuda_paso"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private PasoActividad pasoActividad;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_solicitud", nullable = false, secondPrecision = 0, insertable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDIENTE'")
    @Column(name = "estado", nullable = false)
    private EstadoSolicitudAyuda estado = EstadoSolicitudAyuda.PENDIENTE;

    @Column(name = "fecha_atencion", nullable = true, secondPrecision = 0)
    private LocalDateTime fechaAtencion;
}

