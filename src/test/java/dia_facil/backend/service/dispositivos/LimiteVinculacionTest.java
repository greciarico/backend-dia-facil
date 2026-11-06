package dia_facil.backend.service.dispositivos;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LimiteVinculacionTest {
    @Test void limitesPorOrigenHoraYGlobal() {
        var clock=Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"),ZoneOffset.UTC);
        var limiter=new LimiteVinculacion(clock,2,3,4);
        assertEquals(0,limiter.espera("one"));assertEquals(0,limiter.espera("one"));assertEquals(60,limiter.espera("one"));
        assertEquals(0,limiter.espera("two"));assertEquals(0,limiter.espera("two"));assertEquals(60,limiter.espera("three"));
        var hourly=new LimiteVinculacion(clock,10,1,300);assertEquals(0,hourly.espera("one"));assertEquals(3600,hourly.espera("one"));
    }
}
