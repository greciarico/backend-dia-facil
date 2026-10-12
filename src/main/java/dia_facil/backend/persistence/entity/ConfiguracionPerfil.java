package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.TamanoTexto;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "configuracion_perfil", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", check = {
    @CheckConstraint(name = "chk_config_recordatorio", constraint = "recordatorio_minutos IN (0,5,10,15,30)")
})
public class ConfiguracionPerfil {

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "id_perfil_usuario", nullable = false)
    private Integer idPerfilUsuario;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_perfil_usuario", nullable = false, foreignKey = @ForeignKey(name = "fk_config_perfil"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PerfilUsuario perfilUsuario;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MEDIANO'")
    @Column(name = "tamano_texto", nullable = false)
    private TamanoTexto tamanoTexto = TamanoTexto.MEDIANO;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @ColumnDefault("0")
    @Column(name = "alto_contraste", nullable = false)
    private Boolean altoContraste = false;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @ColumnDefault("1")
    @Column(name = "lectura_voz", nullable = false)
    private Boolean lecturaVoz = true;

    @ColumnDefault("10")
    @Column(name = "recordatorio_minutos", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer recordatorioMinutos = 10;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_actualizacion", nullable = false, secondPrecision = 0, options = "ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime fechaActualizacion;
}

