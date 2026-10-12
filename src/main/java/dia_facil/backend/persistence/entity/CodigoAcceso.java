package dia_facil.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "codigo_acceso", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", indexes = {
    @Index(name = "idx_codigo_acceso_email", columnList = "email,fecha_creacion")
})
public class CodigoAcceso {

    public static CodigoAcceso emitir(String email, String codigoHash, LocalDateTime fechaExpiracion) {
        if (email == null || email.isBlank() || email.length() > 150
                || codigoHash == null || !codigoHash.matches("[0-9a-fA-F]{64}")
                || fechaExpiracion == null) {
            throw new IllegalArgumentException("Correo, hash hexadecimal y expiracion son obligatorios");
        }
        var codigo = new CodigoAcceso();
        codigo.email = email;
        codigo.codigoHash = codigoHash;
        codigo.fechaExpiracion = fechaExpiracion;
        codigo.intentos = 0;
        codigo.usado = false;
        return codigo;
    }

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_codigo_acceso", nullable = false)
    private Integer idCodigoAcceso;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "codigo_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String codigoHash;

    @Column(name = "fecha_expiracion", nullable = false, secondPrecision = 0)
    private LocalDateTime fechaExpiracion;

    @ColumnDefault("0")
    @Column(name = "intentos", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer intentos = 0;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @ColumnDefault("0")
    @Column(name = "usado", nullable = false)
    private Boolean usado = false;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_creacion", nullable = false, secondPrecision = 0, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}

