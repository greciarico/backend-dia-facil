package dia_facil.backend.persistence.entity;

import dia_facil.backend.persistence.enums.TamanoTexto;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "configuracion_cuidador", options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", check = {
    @CheckConstraint(name = "chk_config_cuidador_contraste", constraint = "alto_contraste IN (0,1)")
})
public class ConfiguracionCuidador {
    public static ConfiguracionCuidador inicialPara(Cuidador cuidador) {
        var configuracion = new ConfiguracionCuidador(); configuracion.cuidador = cuidador; return configuracion;
    }
    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "id_cuidador", nullable = false)
    private Integer idCuidador;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuidador", nullable = false, foreignKey = @ForeignKey(name = "fk_config_cuidador"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Cuidador cuidador;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MEDIANO'")
    @Column(name = "tamano_texto", nullable = false)
    private TamanoTexto tamanoTexto = TamanoTexto.MEDIANO;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @ColumnDefault("0")
    @Column(name = "alto_contraste", nullable = false)
    private Boolean altoContraste = false;
}
