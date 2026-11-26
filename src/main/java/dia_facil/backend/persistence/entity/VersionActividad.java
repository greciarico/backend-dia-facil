package dia_facil.backend.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalTime;
import lombok.Getter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@Table(name = "version_actividad", uniqueConstraints = @UniqueConstraint(name = "uq_version_actividad_numero", columnNames = {"id_actividad", "numero"}), options = "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")
public class VersionActividad {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_actividad", nullable = false, foreignKey = @ForeignKey(name = "fk_version_actividad"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Actividad actividad;
    @Column(nullable = false)
    private int numero;
    @Column(nullable = false, length = 80)
    private String nombre;
    @Column(nullable = false, secondPrecision = 0)
    private LocalTime hora;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_icono_actividad", foreignKey = @ForeignKey(name = "fk_version_icono"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private IconoActividad icono;

    protected VersionActividad() {}

    public static VersionActividad de(Actividad actividad) {
        var version = new VersionActividad();
        version.actividad = actividad;
        version.numero = actividad.getRevision();
        version.nombre = actividad.getNombre();
        version.hora = actividad.getHora();
        version.icono = actividad.getIconoActividad();
        return version;
    }

    public static VersionActividad deEjecucion(EntityManager em, EjecucionActividad ejecucion) {
        var actividad = ejecucion.getActividad();
        if (actividad.getRevision() == ejecucion.getRevision()) return de(actividad);
        return em.createQuery("select v from VersionActividad v left join fetch v.icono where v.actividad = :actividad and v.numero = :numero", VersionActividad.class)
            .setParameter("actividad", actividad).setParameter("numero", ejecucion.getRevision()).getSingleResult();
    }

    public static String nombre(EntityManager em, EjecucionActividad ejecucion) {
        return deEjecucion(em, ejecucion).getNombre();
    }
}
