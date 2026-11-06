package dia_facil.backend.infrastructure.mail;

import dia_facil.backend.service.auth.EnvioCodigoAcceso;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnvioCodigoAccesoSmtpTest {
    private static final String FROM = "diafacil-tests@example.com";
    private static final String TO = "destinatario@example.com";

    @Test void preparaCorreoDeTextoPlanoUtf8ConUnSoloDestinatarioYCerosIniciales() throws Exception {
        var sender = mock(JavaMailSender.class);
        var mensaje = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mensaje);
        new EnvioCodigoAccesoSmtp(sender, FROM, "smtp.example.com").enviar(TO, "000123");
        mensaje.saveChanges();

        assertEquals("Tu código de acceso a DiaFacil", mensaje.getSubject());
        assertFalse(mensaje.getSubject().contains("000123"));
        var remitente = (InternetAddress) mensaje.getFrom()[0];
        assertEquals(FROM, remitente.getAddress());
        assertEquals("DiaFacil", remitente.getPersonal());
        assertEquals(1, mensaje.getAllRecipients().length);
        assertEquals(TO, ((InternetAddress) mensaje.getRecipients(Message.RecipientType.TO)[0]).getAddress());
        assertNull(mensaje.getRecipients(Message.RecipientType.CC));
        assertNull(mensaje.getRecipients(Message.RecipientType.BCC));
        assertTrue(mensaje.isMimeType("text/plain"));
        assertTrue(mensaje.getContentType().toLowerCase(java.util.Locale.ROOT).contains("charset=utf-8"));
        assertEquals("Tu código de acceso es:\n\n000123\n\n"
            + "Este código vence 10 minutos después de solicitarlo.\nNo compartas este código.\n\n"
            + "Si no solicitaste este correo, puedes ignorarlo.\n", mensaje.getContent());
        verify(sender, times(1)).send(mensaje);
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"123", "abcdef", "1234567", " 000123 ", "000123\r\n"})
    void rechazaOtpInvalidoAntesDePrepararOEnviar(String codigo) {
        var sender = mock(JavaMailSender.class);
        var adaptador = new EnvioCodigoAccesoSmtp(sender, FROM, "smtp.example.com");
        assertThrows(IllegalArgumentException.class, () -> adaptador.enviar(TO, codigo));
        verifyNoInteractions(sender);
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"invalido", "a@example.com,b@example.com", "Nombre <a@example.com>",
        "a@example.com\r\nBcc: otro@example.com"})
    void rechazaDestinatarioInvalidoOMultipleAntesDeEnviar(String destinatario) {
        var sender = mock(JavaMailSender.class);
        var adaptador = new EnvioCodigoAccesoSmtp(sender, FROM, "smtp.example.com");
        assertThrows(IllegalArgumentException.class, () -> adaptador.enviar(destinatario, "000123"));
        verifyNoInteractions(sender);
    }

    @Test void propagaAutenticacionYErroresDeEnvioSinReintentos() {
        var sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        var adaptador = new EnvioCodigoAccesoSmtp(sender, FROM, "smtp.example.com");
        var autenticacion = new MailAuthenticationException("Autenticacion fallida");
        doThrow(autenticacion).when(sender).send(any(MimeMessage.class));
        assertSame(autenticacion, assertThrows(MailAuthenticationException.class,
            () -> adaptador.enviar(TO, "000123")));
        var envio = new MailSendException("Conexion o envio fallido");
        doThrow(envio).when(sender).send(any(MimeMessage.class));
        assertSame(envio, assertThrows(MailSendException.class, () -> adaptador.enviar(TO, "000123")));
        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test void errorDePreparacionNoIntentaEnviar() throws Exception {
        var sender = mock(JavaMailSender.class);
        var mensaje = mock(MimeMessage.class);
        when(sender.createMimeMessage()).thenReturn(mensaje);
        var causa = new MessagingException("Preparacion fallida");
        doThrow(causa).when(mensaje).setSubject(anyString(), eq(StandardCharsets.UTF_8.name()));
        var adaptador = new EnvioCodigoAccesoSmtp(sender, FROM, "smtp.example.com");
        assertSame(causa, assertThrows(MailPreparationException.class,
            () -> adaptador.enviar(TO, "000123")).getCause());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(EnvioCodigoAccesoSmtp.class);
    }

    private String[] configuracionLocal() throws Exception {
        // Read only the approved template; never load the private application-local.properties.
        var propiedades = new Properties();
        try (var reader = java.nio.file.Files.newBufferedReader(
                java.nio.file.Path.of("application-local.example.properties"), StandardCharsets.UTF_8)) {
            propiedades.load(reader);
        }
        try (var reader = java.nio.file.Files.newBufferedReader(
                java.nio.file.Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            propiedades.load(reader);
        }
        propiedades.setProperty("spring.mail.username", FROM);
        propiedades.setProperty("spring.mail.password", "test-only-not-a-real-credential");
        propiedades.setProperty("diafacil.mail.from", FROM);
        return propiedades.stringPropertyNames().stream()
            .filter(name -> name.startsWith("spring.mail.") || name.startsWith("diafacil.mail."))
            .map(name -> name + "=" + propiedades.getProperty(name)).toArray(String[]::new);
    }

    @Test void sinHostNoRegistraAdaptadorNiSender() {
        contexto().run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(0, context.getBeansOfType(EnvioCodigoAcceso.class).size());
            assertEquals(0, context.getBeansOfType(JavaMailSender.class).size());
        });
    }

    @ParameterizedTest @ValueSource(strings = {"local", "test"})
    void smtpSeActivaEnCualquierPerfilSinConectar(String perfil) throws Exception {
        contexto().withPropertyValues(configuracionLocal())
            .withInitializer(context -> context.getEnvironment().setActiveProfiles(perfil))
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(1, context.getBeansOfType(EnvioCodigoAcceso.class).size());
                var sender = context.getBean(JavaMailSenderImpl.class);
                assertEquals("smtp.gmail.com", sender.getHost());
                assertEquals(587, sender.getPort());
                assertEquals("smtp", sender.getProtocol());
                assertEquals("UTF-8", sender.getDefaultEncoding());
                assertEquals(FROM, sender.getUsername());
                var properties = sender.getJavaMailProperties();
                for (var name : new String[] {"mail.smtp.auth", "mail.smtp.starttls.enable",
                        "mail.smtp.starttls.required", "mail.smtp.ssl.checkserveridentity"}) {
                    assertEquals("true", properties.getProperty(name));
                }
                assertEquals("false", properties.getProperty("mail.smtp.ssl.enable"));
                assertEquals("false", properties.getProperty("mail.debug"));
                for (var name : new String[] {"mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout"}) {
                    assertEquals("5000", properties.getProperty(name));
                }
                assertEquals("false", context.getEnvironment().getProperty("spring.mail.test-connection"));
            });
    }

    @Test void configuracionIncompletaOFallidaSeRechazaSinEnvio() {
        contexto().withPropertyValues("spring.mail.host=smtp.example.com").run(context ->
            assertNotNull(context.getStartupFailure()));
        var sender = mock(JavaMailSender.class);
        assertThrows(IllegalArgumentException.class, () -> new EnvioCodigoAccesoSmtp(sender, FROM, " "));
        assertThrows(IllegalArgumentException.class, () -> new EnvioCodigoAccesoSmtp(sender, "invalido", "smtp.example.com"));
        verifyNoInteractions(sender);
    }
}
