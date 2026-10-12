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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "palabra_clave_pictograma", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_palabra_pictograma", columnNames = {"id_pictograma", "palabra"})
}, indexes = {
    @Index(name = "idx_palabra_pictograma", columnList = "palabra")
})
public class PalabraClavePictograma {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_palabra_clave_pictograma", nullable = false)
    private Integer idPalabraClavePictograma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pictograma", nullable = false, foreignKey = @ForeignKey(name = "fk_palabra_pictograma"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pictograma pictograma;

    @Column(name = "palabra", nullable = false, length = 60)
    private String palabra;
}

