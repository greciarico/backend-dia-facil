package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.Plataforma;
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
@Table(name = "token_push", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", uniqueConstraints = {
    @UniqueConstraint(name = "uq_token_push", columnNames = {"expo_token"})
}, indexes = {
    @Index(name = "idx_token_cuidador", columnList = "id_cuidador")
})
public class TokenPush {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token_push", nullable = false)
    private Integer idTokenPush;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuidador", nullable = false, foreignKey = @ForeignKey(name = "fk_token_cuidador"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Cuidador cuidador;

    @Column(name = "expo_token", nullable = false, length = 255)
    private String expoToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "plataforma", nullable = true)
    private Plataforma plataforma;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_actualizacion", nullable = false, secondPrecision = 0, options = "ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime fechaActualizacion;
}

