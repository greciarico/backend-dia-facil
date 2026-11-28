package dia_facil.backend.service.fotos;

import dia_facil.backend.service.fotos.CloudinaryFotos.Destino;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
public class FotosPerfilService {
    private final ObjectProvider<CloudinaryFotos> adaptador;
    private final JdbcTemplate sql;
    private final String entorno;

    public FotosPerfilService(ObjectProvider<CloudinaryFotos> adaptador, JdbcTemplate sql,
        @Value("${diafacil.cloudinary.entorno:dev}") String entorno) {
        this.adaptador = adaptador;
        this.sql = sql;
        this.entorno = entorno;
    }

    public <T> T crear(Destino destino, MultipartFile foto, Function<String,T> guardar) {
        var cloud = proveedor();
        if (foto.isEmpty() || foto.getSize() > FotoPerfil.MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una fotografía de hasta 5 MiB.");
        byte[] bytes;
        try { bytes = foto.getBytes(); }
        catch (IOException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la fotografía."); }
        var referencia = UUID.randomUUID();
        sql.update("""
            insert into foto_perfil_operacion(referencia, entorno, destino, limpiar_desde)
            values (?, ?, ?, date_add(current_timestamp, interval 10 minute))
            """, referencia.toString(), entorno, destino.name());
        try { cloud.subir(destino, referencia, bytes); }
        catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage()); }
        catch (CloudinaryFotos.FalloProveedor error) { throw noDisponible(); }
        return guardar.apply(referencia.toString());
    }

    // El consumo de la reserva y el perfil se confirman en la misma transacción.
    public static void confirmar(EntityManager em, String referencia) {
        if (referencia == null) return;
        int afectadas = em.createNativeQuery("""
            delete from foto_perfil_operacion where referencia=:ref and limpiar_desde > current_timestamp
            """).setParameter("ref", referencia).executeUpdate();
        if (afectadas != 1) throw noDisponible();
    }

    public static void programarRetirada(EntityManager em, String anterior, String siguiente, String entorno, Destino destino) {
        if (anterior == null || anterior.equals(siguiente)) return;
        em.createNativeQuery("""
            insert into foto_perfil_operacion(referencia, entorno, destino, limpiar_desde)
            values (:ref, :entorno, :destino, current_timestamp)
            """).setParameter("ref", anterior).setParameter("entorno", entorno)
            .setParameter("destino", destino.name()).executeUpdate();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void limpiar() {
        var cloud = adaptador.getIfAvailable();
        if (cloud == null) return;
        var pendientes = sql.query("""
            select referencia, destino from foto_perfil_operacion
            where entorno=? and limpiar_desde <= current_timestamp
              and not exists (select 1 from cuidador c where c.foto_referencia=foto_perfil_operacion.referencia)
              and not exists (select 1 from perfil_usuario p where p.foto_referencia=foto_perfil_operacion.referencia)
            order by limpiar_desde limit 20
            """, (fila, numero) -> new Pendiente(fila.getString(1), Destino.valueOf(fila.getString(2))), entorno);
        for (var pendiente : pendientes) {
            try {
                cloud.eliminar(pendiente.destino(), UUID.fromString(pendiente.referencia()));
                sql.update("delete from foto_perfil_operacion where referencia=? and entorno=?",
                    pendiente.referencia(), entorno);
            } catch (CloudinaryFotos.FalloProveedor error) {
                sql.update("update foto_perfil_operacion set limpiar_desde=date_add(current_timestamp, interval 5 minute) where referencia=?",
                    pendiente.referencia());
            }
        }
    }

    public byte[] leer(Destino destino, String referencia) {
        if (referencia == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try { return proveedor().leer(destino, UUID.fromString(referencia)); }
        catch (CloudinaryFotos.FalloProveedor error) { throw noDisponible(); }
    }

    private CloudinaryFotos proveedor() {
        var cloud = adaptador.getIfAvailable();
        if (cloud == null) throw noDisponible();
        return cloud;
    }

    private static ResponseStatusException noDisponible() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La fotografía no está disponible. Inténtalo nuevamente.");
    }

    private record Pendiente(String referencia, Destino destino) {}
}
