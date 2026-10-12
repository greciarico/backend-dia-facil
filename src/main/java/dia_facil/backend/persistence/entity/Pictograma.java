package dia_facil.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "pictograma", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_pictograma_nombre", columnNames = {"nombre"})
})
public class Pictograma {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pictograma", nullable = false)
    private Integer idPictograma;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "url_imagen", nullable = false, length = 255)
    private String urlImagen;

    @Column(name = "fuente", nullable = true, length = 60)
    private String fuente;
}

