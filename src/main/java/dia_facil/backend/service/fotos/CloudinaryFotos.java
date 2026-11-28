package dia_facil.backend.service.fotos;

import com.cloudinary.Cloudinary;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class CloudinaryFotos {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final Cloudinary cloudinary;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public enum Destino {
        PRUEBAS("pruebas"), CUIDADORES("perfiles/cuidadores"), PERSONAS("perfiles/personas");
        private final String ruta;
        Destino(String ruta) { this.ruta = ruta; }
    }

    private final String entorno;

    CloudinaryFotos(Cloudinary cloudinary, String entorno) {
        if (!"dev".equals(entorno) && !"prod".equals(entorno))
            throw new IllegalArgumentException("El entorno de Cloudinary debe ser dev o prod.");
        this.cloudinary = cloudinary;
        this.entorno = entorno;
    }

    public void subir(Destino destino, UUID referencia, byte[] original) {
        byte[] jpeg = FotoPerfil.normalizar(original);
        String id = id(destino, referencia);
        try {
            var result = cloudinary.uploader().upload(jpeg, Map.of("public_id", id, "resource_type", "image",
                "type", "authenticated", "overwrite", false, "backup", false, "asset_folder", carpeta(destino),
                "connect_timeout", 5000, "timeout", 15000));
            if (result == null || !id.equals(result.get("public_id")) || !"authenticated".equals(result.get("type")))
                throw new IllegalStateException();
        } catch (Exception error) {
            throw new FalloProveedor();
        }
    }

    public byte[] leer(Destino destino, UUID referencia) {
        String id = id(destino, referencia);
        try {
            var url = URI.create(cloudinary.privateDownload(id, "jpg", Map.of("type", "authenticated",
                "resource_type", "image", "expires_at", Instant.now().plusSeconds(60).getEpochSecond())));
            if (!"https".equalsIgnoreCase(url.getScheme())) throw new IllegalStateException();
            var request = HttpRequest.newBuilder(url).timeout(TIMEOUT).GET().build();
            var result = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (result.statusCode() != 200 || result.body().length == 0 || result.body().length > FotoPerfil.MAX_BYTES)
                throw new IllegalStateException();
            return result.body();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new FalloProveedor();
        } catch (Exception error) {
            throw new FalloProveedor();
        }
    }

    public void eliminar(Destino destino, UUID referencia) {
        String id = id(destino, referencia);
        try {
            var result = cloudinary.uploader().destroy(id, Map.of("resource_type", "image", "type", "authenticated",
                "invalidate", true, "connect_timeout", 5000, "timeout", 15000));
            if (result == null || !("ok".equals(result.get("result")) || "not found".equals(result.get("result"))))
                throw new IllegalStateException();
        } catch (Exception error) {
            throw new FalloProveedor();
        }
    }

    String id(Destino destino, UUID referencia) {
        return carpeta(destino) + "/" + java.util.Objects.requireNonNull(referencia);
    }

    private String carpeta(Destino destino) {
        if (destino == Destino.PRUEBAS && !"dev".equals(entorno))
            throw new IllegalArgumentException("El destino de pruebas solo está disponible en dev.");
        return "diafacil/" + entorno + "/" + destino.ruta;
    }

    public static final class FalloProveedor extends RuntimeException {
        FalloProveedor() { super("No se pudo completar la operación de fotografía."); }
    }
}
