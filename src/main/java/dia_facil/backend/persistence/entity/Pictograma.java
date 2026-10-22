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
    @UniqueConstraint(name = "uq_pictograma_arasaac", columnNames = {"id_arasaac"})
})
public class Pictograma {
    public static Pictograma crear(String nombre, String urlImagen, String fuente) {
        var pictograma = new Pictograma(); pictograma.nombre = nombre;
        pictograma.urlImagen = urlImagen; pictograma.fuente = fuente; return pictograma;
    }

    public static Pictograma crearArasaac(String nombre, Integer idArasaac) {
        if (idArasaac == null || idArasaac <= 0) throw new IllegalArgumentException();
        var pictograma = crear(nombre, null, "ARASAAC");
        pictograma.idArasaac = idArasaac;
        return pictograma;
    }


    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pictograma", nullable = false)
    private Integer idPictograma;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    // Legacy locator only. ARASAAC images are resolved from idArasaac in DTOs.
    @Column(name = "url_imagen", length = 255)
    private String urlImagen;

    @Column(name = "id_arasaac")
    private Integer idArasaac;

    @Column(name = "fuente", nullable = true, length = 60)
    private String fuente;
}

