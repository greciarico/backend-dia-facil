package dia_facil.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Entity
@Table(name = "credencial_dispositivo", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    uniqueConstraints = { @UniqueConstraint(name = "uq_dispositivo_token", columnNames = "token_hash"),
        @UniqueConstraint(name = "uq_credencial_dispositivo", columnNames = "id_dispositivo") },
    indexes = @Index(name = "idx_preparacion_dispositivo", columnList = "preparacion_hasta"))
public class CredencialDispositivo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_credencial_dispositivo", nullable = false) private Long idCredencialDispositivo;
    @Column(name = "token_hash", nullable = false, columnDefinition = "binary(32)") private byte[] tokenHash;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_dispositivo", foreignKey = @ForeignKey(name = "fk_credencial_dispositivo"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Dispositivo dispositivo;
    @Column(name = "preparacion_hasta", secondPrecision = 6) private LocalDateTime preparacionHasta;
    public static CredencialDispositivo preparar(byte[] hash, LocalDateTime hasta) {
        var c = new CredencialDispositivo(); c.tokenHash = hash.clone(); c.preparacionHasta = hasta; return c;
    }
    public void vincular(Dispositivo dispositivo) { this.dispositivo = dispositivo; this.preparacionHasta = null; }
    @Override public String toString() { return "CredencialDispositivo[contenido reservado]"; }
}
