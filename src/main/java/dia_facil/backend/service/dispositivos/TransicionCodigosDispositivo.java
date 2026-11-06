package dia_facil.backend.service.dispositivos;

import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.service.perfiles.GeneradorCodigoPerfil;
import jakarta.persistence.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Explicit offline transition only; never invoked by the base data initializer. */
@Service
public class TransicionCodigosDispositivo {
    private final EntityManager em; private final GeneradorCodigoPerfil generator;
    public TransicionCodigosDispositivo(EntityManager em, GeneradorCodigoPerfil generator) { this.em = em; this.generator = generator; }
    public record Auditoria(long actuales, long legacy, long inesperados, long duplicados, boolean codigoUnico) {
        public long total() { return actuales + legacy + inesperados; }
        public void exigirIntegridad() {
            if (inesperados != 0 || duplicados != 0 || !codigoUnico)
                throw new IllegalStateException("Transición detenida: revisar formatos, duplicados y UNIQUE codigo_vinculacion; no se aplicaron cambios.");
        }
    }
    @Transactional(readOnly = true)
    public Auditoria auditar() {
        long actuales = 0, legacy = 0, otros = 0, duplicados = 0;
        var vistos = new HashSet<String>();
        for (String code : em.createQuery("select p.codigoVinculacion from PerfilUsuario p", String.class).getResultList()) {
            if (!vistos.add(code)) duplicados++;
            if (code != null && code.matches(AccesoDispositivoService.FORMATO)) actuales++;
            else if (code != null && code.matches("DF-[0-9]{6}")) legacy++; else otros++;
        }
        boolean codigoUnico = em.unwrap(org.hibernate.Session.class).doReturningWork(TransicionCodigosDispositivo::codigoUnico);
        return new Auditoria(actuales, legacy, otros, duplicados, codigoUnico);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public long aplicar() {
        long changed = 0;
        var perfiles = em.createQuery("select p from PerfilUsuario p order by p.idPerfilUsuario", PerfilUsuario.class)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for (var p : perfiles) em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
        var before = auditar(); before.exigirIntegridad();
        if (before.total() != perfiles.size()) throw new IllegalStateException("Transición detenida: cambió el conjunto de perfiles.");
        record Original(String codigo, LocalDateTime fecha) {}
        var originales = new IdentityHashMap<PerfilUsuario, Original>();
        var reservados = new HashSet<String>();
        for (var p : perfiles) {
            originales.put(p, new Original(p.getCodigoVinculacion(), p.getFechaRenovacionCodigo()));
            reservados.add(p.getCodigoVinculacion());
        }
        for (var p : perfiles) {
            if (p.getCodigoVinculacion().matches(AccesoDispositivoService.FORMATO)) continue;
            if (!p.getCodigoVinculacion().matches("DF-[0-9]{6}")) throw new IllegalStateException("Transición detenida: formato inesperado; no se aplicaron cambios.");
            String nuevo = null;
            for (int i = 0; i < 3 && nuevo == null; i++) {
                String candidate = generator.generar();
                if (!candidate.matches(AccesoDispositivoService.FORMATO)) throw new IllegalStateException("El generador devolvió un formato inesperado.");
                if (!reservados.contains(candidate) && em.createQuery("select count(p) from PerfilUsuario p where p.codigoVinculacion=:c", Long.class)
                    .setParameter("c", candidate).getSingleResult() == 0) nuevo = candidate;
            }
            if (nuevo == null) throw new IllegalStateException("Transición detenida: colisión; no se aplicaron cambios.");
            p.setCodigoVinculacion(nuevo); reservados.add(nuevo); changed++;
        }
        em.flush();
        for (var p : perfiles) {
            em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
            var original = originales.get(p);
            if (!Objects.equals(original.fecha(), p.getFechaRenovacionCodigo())
                || original.codigo().matches(AccesoDispositivoService.FORMATO) && !original.codigo().equals(p.getCodigoVinculacion()))
                throw new IllegalStateException("Transición detenida: cambió una fecha o un código previamente vigente.");
        }
        var after = auditar(); after.exigirIntegridad();
        if (after.legacy() != 0 || after.total() != before.total() || changed != before.legacy())
            throw new IllegalStateException("Transición detenida: las comprobaciones finales no coinciden; se revierte la operación.");
        return changed;
    }

    /** JDBC metadata only: no schema repair or DDL. Accept an equivalent single-column UNIQUE. */
    static boolean codigoUnico(Connection connection) throws SQLException {
        var indexes = new HashMap<String, List<String>>();
        try (var rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "perfil_usuario", true, false)) {
            while (rows.next()) {
                String index = rows.getString("INDEX_NAME"), column = rows.getString("COLUMN_NAME");
                if (index != null && column != null && !rows.getBoolean("NON_UNIQUE"))
                    indexes.computeIfAbsent(index, key -> new ArrayList<>()).add(column.toLowerCase(Locale.ROOT));
            }
        }
        return indexes.containsValue(List.of("codigo_vinculacion"));
    }
}
