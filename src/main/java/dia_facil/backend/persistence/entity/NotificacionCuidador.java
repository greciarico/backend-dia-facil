package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.TipoNotificacion;
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
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
@Table(name = "notificacion_cuidador", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {@jakarta.persistence.UniqueConstraint(name="uq_notif_solicitud",columnNames="id_solicitud_ayuda"), @jakarta.persistence.UniqueConstraint(name="uq_notif_ejecucion",columnNames="id_ejecucion_actividad")}, indexes = {
    @Index(name = "idx_notif_cuidador_fecha", columnList = "id_cuidador,fecha_creacion"),
    @Index(name = "idx_notif_cuidador_leida", columnList = "id_cuidador,leida")
})
public class NotificacionCuidador {
    public static NotificacionCuidador ayuda(SolicitudAyuda s) {
        var n=new NotificacionCuidador(); n.perfilUsuario=s.getPerfilUsuario(); n.solicitudAyuda=s;
        n.tipo=s.getActividad()==null?TipoNotificacion.AYUDA_GENERAL:TipoNotificacion.AYUDA_CONTEXTUAL;
        n.titulo="Solicitud de ayuda";
        n.mensaje=n.perfilUsuario.getNombre()+" necesita tu ayuda"+(s.getActividad()==null?"":": se quedó atascada en el paso "+s.getPosicionPaso()+" de "+s.getActividad().getNombre()); return n;
    }
    public static NotificacionCuidador completada(EjecucionActividad e) {
        var n=new NotificacionCuidador(); n.perfilUsuario=e.getActividad().getPerfilUsuario(); n.ejecucionActividad=e;
        n.tipo=TipoNotificacion.ACTIVIDAD_COMPLETADA; n.titulo="Actividad completada";
        n.mensaje=n.perfilUsuario.getNombre()+" completó: "+e.getActividad().getNombre(); return n;
    }


    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion_cuidador", nullable = false)
    private Integer idNotificacionCuidador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(value = {
        @JoinColumn(name = "id_perfil_usuario", referencedColumnName = "id_perfil_usuario", nullable = false),
        @JoinColumn(name = "id_cuidador", referencedColumnName = "id_cuidador", nullable = false)
    }, foreignKey = @ForeignKey(name = "fk_notif_perfil_cuidador"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PerfilUsuario perfilUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoNotificacion tipo;

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "mensaje", nullable = false, length = 255)
    private String mensaje;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_ejecucion_actividad", nullable = true, foreignKey = @ForeignKey(name = "fk_notif_ejecucion"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private EjecucionActividad ejecucionActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_solicitud_ayuda", nullable = true, foreignKey = @ForeignKey(name = "fk_notif_solicitud"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private SolicitudAyuda solicitudAyuda;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_creacion", nullable = false, secondPrecision = 0, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @ColumnDefault("0")
    @Column(name = "leida", nullable = false)
    private Boolean leida = false;
}

