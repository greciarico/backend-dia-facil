package dia_facil.backend.infrastructure.mail;

import dia_facil.backend.service.auth.EnvioCodigoAcceso;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host", matchIfMissing = false)
public class EnvioCodigoAccesoSmtp implements EnvioCodigoAcceso {
    private final JavaMailSender mailSender;
    private final String remitente;

    public EnvioCodigoAccesoSmtp(JavaMailSender mailSender,
            @Value("${diafacil.mail.from}") String remitente,
            @Value("${spring.mail.host}") String host) {
        this.mailSender = Objects.requireNonNull(mailSender);
        if (host == null || host.isBlank()) throw new IllegalArgumentException("Host SMTP obligatorio");
        direccion(remitente);
        this.remitente = remitente;
    }

    @Override
    public void enviar(String email, String codigo) {
        var destinatario = direccion(email);
        if (codigo == null || !codigo.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("El OTP debe contener seis digitos");
        }
        var mensaje = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(remitente, "DiaFacil", StandardCharsets.UTF_8.name()));
            helper.setTo(destinatario);
            helper.setSubject("Tu código de acceso a DiaFacil");
            helper.setText("Tu código de acceso es:\n\n" + codigo
                + "\n\nEste código vence 10 minutos después de solicitarlo.\n"
                + "No compartas este código.\n\n"
                + "Si no solicitaste este correo, puedes ignorarlo.\n", false);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("No se pudo preparar el correo de acceso", ex);
        }
        // MailException propagates to the service. Never log the message or retry here.
        mailSender.send(mensaje);
    }

    private static InternetAddress direccion(String email) {
        if (email == null || email.isBlank() || email.contains("\r") || email.contains("\n")) {
            throw new IllegalArgumentException("Direccion de correo invalida");
        }
        try {
            var address = new InternetAddress(email, true);
            address.validate();
            if (address.getPersonal() != null || !email.equals(address.getAddress())) {
                throw new IllegalArgumentException("Se requiere una unica direccion de correo");
            }
            return address;
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Direccion de correo invalida");
        }
    }
}
