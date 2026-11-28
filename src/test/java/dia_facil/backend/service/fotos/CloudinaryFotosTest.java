package dia_facil.backend.service.fotos;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import static dia_facil.backend.service.fotos.CloudinaryFotos.Destino.PRUEBAS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryFotosTest {
    @Test void normalizaPngYJpeg() throws Exception {
        for (String formato : new String[]{"png", "jpg"}) {
            var imagen = ImageIO.read(new ByteArrayInputStream(FotoPerfil.normalizar(imagen(formato))));
            assertEquals(512, imagen.getWidth());
            assertEquals(512, imagen.getHeight());
        }
    }

    @Test void rechazaFormatoTamanoYDatosInvalidos() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> FotoPerfil.normalizar(imagen("gif")));
        assertThrows(IllegalArgumentException.class, () -> FotoPerfil.normalizar(new byte[FotoPerfil.MAX_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> FotoPerfil.normalizar(new byte[]{1, 2, 3}));
    }

    @Test void configuracionOptativaYCompleta() {
        var runner = new ApplicationContextRunner().withUserConfiguration(CloudinaryFotosConfiguration.class);
        runner.run(context -> assertFalse(context.containsBean("cloudinaryFotos")));
        runner.withPropertyValues("diafacil.cloudinary.enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("diafacil.cloudinary.enabled=true", "diafacil.cloudinary.cloud-name=demo",
            "diafacil.cloudinary.api-key=test", "diafacil.cloudinary.api-secret=test")
            .run(context -> assertNotNull(context.getBean(CloudinaryFotos.class)));
    }

    @Test void subePrivadoYEliminaDeFormaIdempotente() throws Exception {
        var sdk = mock(Cloudinary.class);
        var uploader = mock(Uploader.class);
        when(sdk.uploader()).thenReturn(uploader);
        var referencia = UUID.randomUUID();
        String id = "diafacil/dev/pruebas/" + referencia;
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(call -> {
            Map<?, ?> options = call.getArgument(1);
            assertEquals(id, options.get("public_id"));
            assertEquals("diafacil/dev/pruebas", options.get("asset_folder"));
            assertEquals("authenticated", options.get("type"));
            assertEquals(false, options.get("overwrite"));
            return Map.of("public_id", id, "type", "authenticated");
        });
        when(uploader.destroy(eq(id), anyMap())).thenReturn(Map.of("result", "not found"));
        var fotos = new CloudinaryFotos(sdk, "dev");
        fotos.subir(PRUEBAS, referencia, imagen("png"));
        fotos.eliminar(PRUEBAS, referencia);
        verify(uploader).destroy(eq(id), argThat(options -> Boolean.TRUE.equals(options.get("invalidate"))));
        doThrow(new IOException("dato privado")).when(uploader).upload(any(byte[].class), anyMap());
        var fallo = assertThrows(CloudinaryFotos.FalloProveedor.class, () -> fotos.subir(PRUEBAS, referencia, imagen("png")));
        assertNull(fallo.getCause());
        assertFalse(fallo.getMessage().contains("dato privado"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DIAFACIL_CLOUDINARY_SMOKE", matches = "true")
    void cicloRealConImagenSintetica() {
        contextoDesde("file:./application-local.properties").run(context -> {
            assertTrue(context.getStartupFailure() == null, "No se pudo cargar la configuración local de fotografías.");
            assertEquals("dev", context.getEnvironment().getProperty("diafacil.cloudinary.entorno", "dev"));
            assertTrue(context.containsBean("cloudinaryFotos"), "Habilita Cloudinary en la configuración local.");
            comprobarCiclo(context.getBean(CloudinaryFotos.class),
                context.getEnvironment().getProperty("diafacil.cloudinary.cloud-name"));
        });
    }

    private static ApplicationContextRunner contextoDesde(String ubicacion) {
        return new ApplicationContextRunner().withPropertyValues("spring.config.location=" + ubicacion)
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(CloudinaryFotosConfiguration.class);
    }

    private static void comprobarCiclo(CloudinaryFotos fotos, String cloud) throws Exception {
        var referencia = UUID.randomUUID();
        try {
            fotos.subir(PRUEBAS, referencia, imagen("png"));
            var anonima = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "https://res.cloudinary.com/" + cloud + "/image/authenticated/" + fotos.id(PRUEBAS, referencia) + ".jpg"))
                .timeout(java.time.Duration.ofSeconds(15)).GET().build();
            var http = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).build();
            int estado = http.send(anonima, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
            assertTrue(estado == 401 || estado == 403 || estado == 404, "Estado anónimo inesperado: " + estado);
            var png = ImageIO.read(new ByteArrayInputStream(fotos.leer(PRUEBAS, referencia)));
            assertEquals(512, png.getWidth());
            assertEquals(512, png.getHeight());
        } finally {
            try { fotos.eliminar(PRUEBAS, referencia); }
            catch (CloudinaryFotos.FalloProveedor error) { fail("Limpieza pendiente para referencia de prueba " + referencia); }
        }
    }

    @Test void cargaArchivoSinteticoSinDatasource(@org.junit.jupiter.api.io.TempDir java.nio.file.Path carpeta) throws Exception {
        var archivo = carpeta.resolve("application-local.properties");
        java.nio.file.Files.writeString(archivo, """
            diafacil.cloudinary.enabled=true
            diafacil.cloudinary.cloud-name=demo
            diafacil.cloudinary.api-key=test
            diafacil.cloudinary.api-secret=test
            diafacil.cloudinary.entorno=prod
            """);
        contextoDesde(archivo.toUri().toString()).run(context -> {
            var fotos = context.getBean(CloudinaryFotos.class);
            var referencia = UUID.randomUUID();
            assertEquals("diafacil/prod/perfiles/cuidadores/" + referencia, fotos.id(CloudinaryFotos.Destino.CUIDADORES, referencia));
            assertEquals("diafacil/prod/perfiles/personas/" + referencia, fotos.id(CloudinaryFotos.Destino.PERSONAS, referencia));
            assertThrows(IllegalArgumentException.class, () -> fotos.id(PRUEBAS, referencia));
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        });
    }

    private static byte[] imagen(String formato) throws IOException {
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB), formato, bytes));
        return bytes.toByteArray();
    }
}
