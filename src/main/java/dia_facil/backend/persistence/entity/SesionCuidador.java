package dia_facil.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** All timestamps in this table are explicit UTC; not profile devices. */
@Getter @Setter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Entity
@Table(name = "sesion_cuidador", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", indexes = {
    @Index(name = "idx_sesion_cuidador", columnList = "id_cuidador")
})
public class SesionCuidador {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_sesion")
    private Long idSesion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.RESTRICT)
    @JoinColumn(name = "id_cuidador", nullable = false, foreignKey = @ForeignKey(name = "fk_sesion_cuidador"))
    private Cuidador cuidador;
    @Column(name = "creada_en", nullable = false, secondPrecision = 6) private LocalDateTime creadaEn;
    @Column(name = "inactividad_hasta", nullable = false, secondPrecision = 6) private LocalDateTime inactividadHasta;
    @Column(name = "vence_en", nullable = false, secondPrecision = 6) private LocalDateTime venceEn;
    @Column(name = "revocada_en", secondPrecision = 6) private LocalDateTime revocadaEn;
    public static SesionCuidador crear(Cuidador cuidador, LocalDateTime ahora, LocalDateTime inactividad, LocalDateTime absoluta) {
        var sesion = new SesionCuidador(); sesion.cuidador = cuidador; sesion.creadaEn = ahora;
        sesion.inactividadHasta = inactividad; sesion.venceEn = absoluta; return sesion;
    }
}
