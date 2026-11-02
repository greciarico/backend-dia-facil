package dia_facil.backend.persistence.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
@Getter @Setter @NoArgsConstructor @Entity
@Table(name="entrega_push",options="DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",uniqueConstraints=@UniqueConstraint(name="uq_entrega_destino",columnNames={"id_notificacion_cuidador","id_token_push","generacion"}),indexes=@Index(name="idx_entrega_pendiente",columnList="estado,proxima_en"))
public class EntregaPush {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_entrega") private Long idEntrega;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_notificacion_cuidador",nullable=false,foreignKey=@ForeignKey(name="fk_entrega_notificacion")) @OnDelete(action=OnDeleteAction.CASCADE) private NotificacionCuidador notificacion;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_token_push",nullable=false,foreignKey=@ForeignKey(name="fk_entrega_token")) @OnDelete(action=OnDeleteAction.CASCADE) private TokenPush token;
 @Column(name="generacion",nullable=false) private long generacion;
 @Column(name="estado",nullable=false,length=32) private String estado="PENDIENTE";
 @Column(name="intentos",nullable=false) private int intentos;
 @Column(name="proxima_en",nullable=false,secondPrecision=6) private LocalDateTime proximaEn;
 @Column(name="vence_en",nullable=false,secondPrecision=6) private LocalDateTime venceEn;
 @Column(name="bloqueo_hasta",secondPrecision=6) private LocalDateTime bloqueoHasta;
 @Column(name="arrendamiento",length=36) private String arrendamiento;
 @Column(name="ticket",length=255) private String ticket;
 @Column(name="error_codigo",length=64) private String errorCodigo;
}
