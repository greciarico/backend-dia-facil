package dia_facil.backend.service.dispositivos;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.service.auth.CriptografiaSesion;
import dia_facil.backend.service.perfiles.PerfilResumen;
import jakarta.persistence.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "diafacil.dispositivos.enabled", havingValue = "true")
public class AccesoDispositivoService {
    public static final String FORMATO = "DF-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{16}";
    private final EntityManager em;
    private final CriptografiaSesion crypto = new CriptografiaSesion(new SecureRandom());
    private volatile boolean habilitado;
    public AccesoDispositivoService(EntityManager em) { this.em = em; }
    public void habilitar() { habilitado = true; }
    private void disponible() { if (!habilitado) throw new RechazoDispositivo(503, "SERVICIO_NO_DISPONIBLE", "La conexión todavía no está disponible."); }
    public record Preparacion(String credencialDispositivo) {
        @Override public String toString() { return "Preparacion[contenido reservado]"; }
    }
    public record Estado(String estado, PerfilResumen perfil) {}
    public record Vinculacion(String estado, PerfilResumen perfil, String credencialDispositivo) {
        @Override public String toString() { return "Vinculacion[contenido reservado]"; }
    }
    public record Identidad(Long idCredencial, Integer idPerfilUsuario) implements java.security.Principal {
        @Override public String getName() { return idCredencial.toString(); }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Preparacion preparar() {
        disponible();
        String token = crypto.generar("du");
        em.persist(CredencialDispositivo.preparar(CriptografiaSesion.hash(token), ahora().plusMinutes(10)));
        em.flush(); return new Preparacion(token);
    }
    @Transactional(readOnly = true)
    public Identidad autenticar(String token) {
        disponible();
        if (!CriptografiaSesion.valido(token, "du")) throw RechazoDispositivo.invalida();
        var c = em.createQuery("select c from CredencialDispositivo c where c.tokenHash=:hash", CredencialDispositivo.class)
            .setParameter("hash", CriptografiaSesion.hash(token)).getResultStream().findFirst().orElseThrow(RechazoDispositivo::invalida);
        validar(c);
        return new Identidad(c.getIdCredencialDispositivo(), c.getDispositivo() == null ? null : c.getDispositivo().getPerfilUsuario().getIdPerfilUsuario());
    }
    private void validar(CredencialDispositivo c) {
        if (c == null) throw RechazoDispositivo.invalida();
        if (c.getDispositivo() == null) {
            if (c.getPreparacionHasta() == null || !ahora().isBefore(c.getPreparacionHasta())) throw RechazoDispositivo.invalida();
        } else if (c.getDispositivo().getEstado() != EstadoDispositivo.ACTIVO
            || c.getDispositivo().getPerfilUsuario().getEstado() != EstadoPerfil.ACTIVO) throw RechazoDispositivo.invalida();
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Estado vincular(Long credencial, String codigo, String nombre) {
        var result = vincular(credencial, codigo, nombre, List.of());
        return new Estado(result.estado(), result.perfil());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Vinculacion vincular(Long credencial, String codigo, String nombre, List<String> credencialesPrevias) {
        disponible();
        var existing = em.find(CredencialDispositivo.class, credencial); validar(existing);
        if (existing.getDispositivo() != null) throw new RechazoDispositivo(409, "DISPOSITIVO_YA_VINCULADO", "Consulta tu agenda conectada.");
        if (codigo == null || !codigo.matches(FORMATO)) throw RechazoDispositivo.codigoInvalido();
        if (nombre == null || nombre.isBlank() || nombre.strip().length() > 80)
            throw new RechazoDispositivo(400, "SOLICITUD_INVALIDA", "Revisa la información del dispositivo.");
        var found = em.createQuery("select p.idPerfilUsuario from PerfilUsuario p where p.codigoVinculacion=:codigo and p.estado=:estado", Integer.class)
            .setParameter("codigo", codigo).setParameter("estado", EstadoPerfil.ACTIVO).getResultStream().findFirst().orElseThrow(RechazoDispositivo::codigoInvalido);
        // Same row/order as E1: renewal and binding cannot commit against different snapshots.
        var perfil = em.find(PerfilUsuario.class, found, LockModeType.PESSIMISTIC_WRITE);
        if (perfil == null) throw RechazoDispositivo.codigoInvalido();
        em.refresh(perfil, LockModeType.PESSIMISTIC_WRITE);
        if (perfil.getEstado() != EstadoPerfil.ACTIVO || !codigo.equals(perfil.getCodigoVinculacion())) throw RechazoDispositivo.codigoInvalido();
        var c = em.find(CredencialDispositivo.class, credencial, LockModeType.PESSIMISTIC_WRITE);
        if (c == null) throw RechazoDispositivo.invalida();
        em.refresh(c, LockModeType.PESSIMISTIC_WRITE); validar(c);
        if (c.getDispositivo() != null) throw new RechazoDispositivo(409, "DISPOSITIVO_YA_VINCULADO", "Consulta tu agenda conectada.");
        for (String proof : new LinkedHashSet<>(credencialesPrevias == null ? List.<String>of() : credencialesPrevias)) {
            if (!CriptografiaSesion.valido(proof, "du")) continue;
            var previous = em.createQuery("select c from CredencialDispositivo c join fetch c.dispositivo d where c.tokenHash=:hash and d.perfilUsuario.idPerfilUsuario=:perfil and d.estado=:estado", CredencialDispositivo.class)
                .setParameter("hash", CriptografiaSesion.hash(proof)).setParameter("perfil", found).setParameter("estado", EstadoDispositivo.ACTIVO)
                .getResultStream().findFirst();
            if (previous.isEmpty()) continue;
            var saved = previous.get(); em.refresh(saved, LockModeType.PESSIMISTIC_WRITE);
            var device = saved.getDispositivo();
            if (device == null) continue;
            em.refresh(device, LockModeType.PESSIMISTIC_WRITE);
            if (device.getEstado() != EstadoDispositivo.ACTIVO || !found.equals(device.getPerfilUsuario().getIdPerfilUsuario())) continue;
            device.setFechaUltimaActividad(ahora());
            var result = estado(saved);
            return new Vinculacion(result.estado(), result.perfil(), proof);
        }
        var dispositivo = Dispositivo.vincular(perfil, nombre.strip(), ahora()); em.persist(dispositivo);
        c.vincular(dispositivo); em.flush(); var result = estado(c); return new Vinculacion(result.estado(), result.perfil(), null);
    }
    @Transactional(readOnly = true)
    public PerfilResumen perfilActivo(Identidad principal) {
        disponible();
        if (principal == null) throw RechazoDispositivo.invalida();
        var c = em.find(CredencialDispositivo.class, principal.idCredencial()); validar(c);
        if (c.getDispositivo() == null) throw RechazoDispositivo.invalida();
        return estado(c).perfil();
    }
    @Transactional
    public Estado consultar(Long credencial) {
        disponible();
        var c = em.find(CredencialDispositivo.class, credencial); validar(c);
        if (c.getDispositivo() != null) c.getDispositivo().setFechaUltimaActividad(ahora());
        return estado(c);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void salir(Long credencial) {
        disponible();
        var found = em.find(CredencialDispositivo.class, credencial);
        if (found == null) return;
        if (found.getDispositivo() != null) {
            var perfil = found.getDispositivo().getPerfilUsuario();
            em.lock(perfil, LockModeType.PESSIMISTIC_WRITE);
        }
        var c = em.find(CredencialDispositivo.class, credencial, LockModeType.PESSIMISTIC_WRITE);
        if (c == null) return; em.refresh(c, LockModeType.PESSIMISTIC_WRITE);
        if (c.getDispositivo() == null) em.remove(c);
        else throw new RechazoDispositivo(403, "ACCESO_DENEGADO", "La desvinculación corresponde al cuidador.");
    }
    @Transactional
    public void limpiarPreparaciones() {
        em.createQuery("delete from CredencialDispositivo c where c.dispositivo is null and c.preparacionHasta <= :ahora")
            .setParameter("ahora", ahora()).executeUpdate();
    }
    private Estado estado(CredencialDispositivo c) {
        if (c.getDispositivo() == null) return new Estado("PENDIENTE", null);
        var p = c.getDispositivo().getPerfilUsuario();
        return new Estado("VINCULADO", new PerfilResumen(p.getIdPerfilUsuario(), p.getNombre(), p.getAvatar()));
    }
    private LocalDateTime ahora() { return em.createQuery("select local datetime", LocalDateTime.class).getSingleResult(); }
}
