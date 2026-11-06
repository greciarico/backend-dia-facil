package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.service.datosbase.DatosBaseStore;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

public class JpaDatosBaseStore implements DatosBaseStore {
    private final EntityManager em;
    public JpaDatosBaseStore(EntityManager em) { this.em = em; }

    public Optional<Icono> icono(String codigo) {
        return em.createQuery("select i from IconoActividad i where i.codigo=:clave", IconoActividad.class)
            .setParameter("clave", codigo).getResultList().stream().findFirst().map(i -> new Icono(i.getIdIconoActividad(), i.getNombre()));
    }
    public Icono crearIcono(String codigo, String nombre) {
        var i = IconoActividad.crear(codigo, nombre); em.persist(i); return new Icono(i.getIdIconoActividad(), i.getNombre());
    }
    public boolean palabraIcono(Integer icono, String palabra) {
        return em.createQuery("select count(k) from PalabraClaveIconoActividad k where k.iconoActividad.idIconoActividad=:id and k.palabra=:palabra", Long.class)
            .setParameter("id", icono).setParameter("palabra", palabra).getSingleResult() > 0;
    }
    public void crearPalabraIcono(Integer icono, String palabra) {
        em.persist(PalabraClaveIconoActividad.crear(em.getReference(IconoActividad.class, icono), palabra));
    }
    public Optional<Pictograma> pictograma(String nombre) {
        var filas = em.createQuery("select p from Pictograma p where p.nombre=:clave and p.idArasaac is null", dia_facil.backend.persistence.entity.Pictograma.class)
            .setParameter("clave", nombre).getResultList();
        if (filas.size() > 1) throw new IllegalStateException("Pictogramas legacy ambiguos. Revise la auditoría ARASAAC; no se modificaron registros.");
        return filas.stream().findFirst().map(JpaDatosBaseStore::resumen);
    }
    public Optional<Pictograma> pictogramaArasaac(Integer idArasaac) {
        return em.createQuery("select p from Pictograma p where p.idArasaac=:id", dia_facil.backend.persistence.entity.Pictograma.class)
            .setParameter("id", idArasaac).getResultList().stream().findFirst().map(JpaDatosBaseStore::resumen);
    }
    public Pictograma crearPictogramaArasaac(String nombre, Integer idArasaac) {
        var p = dia_facil.backend.persistence.entity.Pictograma.crearArasaac(nombre, idArasaac); em.persist(p); return resumen(p);
    }
    private static Pictograma resumen(dia_facil.backend.persistence.entity.Pictograma p) {
        return new Pictograma(p.getIdPictograma(), p.getUrlImagen(), p.getFuente(), p.getIdArasaac(), p.getNombre());
    }
    public Pictograma crearPictograma(String nombre, String url, String fuente) {
        var p = dia_facil.backend.persistence.entity.Pictograma.crear(nombre, url, fuente); em.persist(p);
        return resumen(p);
    }
    public boolean palabraPictograma(Integer pictograma, String palabra) {
        return em.createQuery("select count(k) from PalabraClavePictograma k where k.pictograma.idPictograma=:id and k.palabra=:palabra", Long.class)
            .setParameter("id", pictograma).setParameter("palabra", palabra).getSingleResult() > 0;
    }
    public void crearPalabraPictograma(Integer pictograma, String palabra) {
        em.persist(PalabraClavePictograma.crear(em.getReference(dia_facil.backend.persistence.entity.Pictograma.class, pictograma), palabra));
    }
    public Optional<Plantilla> plantilla(String nombre) {
        return em.createQuery("select t from PlantillaActividad t where t.nombre=:clave", PlantillaActividad.class)
            .setParameter("clave", nombre).getResultList().stream().findFirst()
            .map(t -> new Plantilla(t.getIdPlantillaActividad(), t.getIconoActividad() == null ? null : t.getIconoActividad().getIdIconoActividad()));
    }
    public Plantilla crearPlantilla(String nombre, Integer icono) {
        var t = PlantillaActividad.crear(nombre, icono == null ? null : em.getReference(IconoActividad.class, icono)); em.persist(t);
        return new Plantilla(t.getIdPlantillaActividad(), icono);
    }
    public List<Paso> pasos(Integer plantilla) {
        return em.createQuery("select p from PlantillaPaso p where p.plantillaActividad.idPlantillaActividad=:id order by p.orden", PlantillaPaso.class)
            .setParameter("id", plantilla).getResultList().stream().map(p -> new Paso(p.getOrden(), p.getDescripcion(),
                p.getPictograma() == null ? null : p.getPictograma().getIdPictograma())).toList();
    }
    public void crearPaso(Integer plantilla, Integer orden, String descripcion, Integer pictograma) {
        em.persist(PlantillaPaso.crear(em.getReference(PlantillaActividad.class, plantilla), orden, descripcion,
            pictograma == null ? null : em.getReference(dia_facil.backend.persistence.entity.Pictograma.class, pictograma)));
    }
    public boolean palabraPlantilla(Integer plantilla, String palabra) {
        return em.createQuery("select count(k) from PalabraClaveActividad k where k.plantillaActividad.idPlantillaActividad=:id and k.palabra=:palabra", Long.class)
            .setParameter("id", plantilla).setParameter("palabra", palabra).getSingleResult() > 0;
    }
    public void crearPalabraPlantilla(Integer plantilla, String palabra) {
        em.persist(PalabraClaveActividad.crear(em.getReference(PlantillaActividad.class, plantilla), palabra));
    }
}

