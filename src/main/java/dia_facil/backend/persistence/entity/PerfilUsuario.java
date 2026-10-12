package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.EstadoPerfil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.generator.EventType;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "perfil_usuario", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_perfil_codigo", columnNames = {"codigo_vinculacion"}),
    // Hibernate orders the composite association by its referenced properties.
    // Keep its backing unique key in the same order so MySQL can create that FK.
    @UniqueConstraint(name = "uq_perfil_cuidador", columnNames = {"id_cuidador", "id_perfil_usuario"})
}, indexes = {
    @Index(name = "idx_perfil_cuidador_nombre", columnList = "id_cuidador,nombre")
})
public class PerfilUsuario {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_perfil_usuario", nullable = false)
    private Integer idPerfilUsuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuidador", nullable = false, foreignKey = @ForeignKey(name = "fk_perfil_cuidador"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Cuidador cuidador;

    // Register the referenced column before association second passes; cuidador remains its writer.
    @Setter(AccessLevel.NONE)
    @Column(name = "id_cuidador", nullable = false, insertable = false, updatable = false)
    private Integer idCuidador;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "avatar", nullable = true, length = 255)
    private String avatar;

    @Column(name = "codigo_vinculacion", nullable = false, length = 20)
    private String codigoVinculacion;

    @Column(name = "fecha_renovacion_codigo", nullable = true, secondPrecision = 0)
    private LocalDateTime fechaRenovacionCodigo;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado", nullable = false)
    private EstadoPerfil estado = EstadoPerfil.ACTIVO;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_creacion", nullable = false, secondPrecision = 0, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}

