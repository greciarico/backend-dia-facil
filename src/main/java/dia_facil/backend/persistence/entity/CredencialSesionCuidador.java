package dia_facil.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Entity
@Table(name = "credencial_sesion_cuidador", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_credencial_access", columnNames = "access_hash"),
    @UniqueConstraint(name = "uq_credencial_refresh", columnNames = "refresh_hash"),
    @UniqueConstraint(name = "uq_credencial_generacion", columnNames = {"id_sesion", "generacion"})
})
public class CredencialSesionCuidador {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_credencial") private Long idCredencial;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.RESTRICT)
    @JoinColumn(name = "id_sesion", nullable = false, foreignKey = @ForeignKey(name = "fk_credencial_sesion")) private SesionCuidador sesion;
    @Column(name = "generacion", nullable = false) private Integer generacion;
    @Column(name = "access_hash", nullable = false, length = 32, columnDefinition = "binary(32)") private byte[] accessHash;
    @Column(name = "refresh_hash", nullable = false, length = 32, columnDefinition = "binary(32)") private byte[] refreshHash;
    @Column(name = "access_hasta", nullable = false, secondPrecision = 6) private LocalDateTime accessHasta;
    @Column(name = "consumida_en", secondPrecision = 6) private LocalDateTime consumidaEn;
    public static CredencialSesionCuidador crear(SesionCuidador sesion, int generacion, byte[] access, byte[] refresh, LocalDateTime venceAccess) {
        var c = new CredencialSesionCuidador(); c.sesion = sesion; c.generacion = generacion;
        c.accessHash = access.clone(); c.refreshHash = refresh.clone(); c.accessHasta = venceAccess; return c;
    }
}
