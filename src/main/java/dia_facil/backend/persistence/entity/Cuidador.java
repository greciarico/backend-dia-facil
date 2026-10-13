package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.EstadoCuidador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "cuidador", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_cuidador_email", columnNames = {"email"})
})
public class Cuidador {

    public static Cuidador crear(String email, String nombre) {
        if (email == null || email.isBlank() || email.length() > 150
                || nombre == null || nombre.isBlank() || nombre.strip().length() > 100) {
            throw new IllegalArgumentException("Correo y nombre son obligatorios y deben respetar sus longitudes");
        }
        var cuidador = new Cuidador();
        cuidador.email = email;
        cuidador.nombre = nombre.strip();
        cuidador.estado = EstadoCuidador.ACTIVO;
        return cuidador;
    }

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cuidador", nullable = false)
    private Integer idCuidador;

    @Column(name = "nombre", nullable = true, length = 100)
    private String nombre;

    @Column(name = "avatar_id", nullable = true, length = 32)
    private String avatarId;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado", nullable = false)
    private EstadoCuidador estado = EstadoCuidador.ACTIVO;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_creacion", nullable = false, secondPrecision = 0, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}

