package dia_facil.backend.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

/** Durable ordering fence. It is independent of token ownership and delivery generation. */
@Entity
@Table(name = "control_registro_push", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")
public class ControlRegistroPush {
    @Id
    @Column(name = "id_instalacion", length = 36, nullable = false)
    private String idInstalacion;

    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private long version;

    protected ControlRegistroPush() {}

    public long getVersion() { return version; }
    public void avanzar() { version = Math.incrementExact(version); }
}
