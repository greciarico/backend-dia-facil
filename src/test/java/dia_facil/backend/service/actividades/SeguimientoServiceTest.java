package dia_facil.backend.service.actividades;

import java.util.List;
import java.util.stream.IntStream;
import java.time.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import dia_facil.backend.persistence.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeguimientoServiceTest {
    @Test void consultaFechasCubiertasConElRegistroYNoInventaDenominadorAnterior() {
        var em = mock(EntityManager.class);
        var registro = mock(RegistroSeguimiento.class);
        var perfil = PerfilUsuario.crear(Cuidador.crear("c@example.invalid", "Cuidador"), "Ana", null, "DF-7KQ9W6H2M8V3X5R2");
        ReflectionTestUtils.setField(perfil, "idPerfilUsuario", 12);
        ReflectionTestUtils.setField(perfil, "fechaCreacion", LocalDateTime.parse("2026-11-02T02:00"));
        var persona = consulta(List.of(perfil));
        var activas = consulta(List.<Actividad>of());
        var ejecuciones = consulta(List.<EjecucionActividad>of());
        var historial = consulta(List.<Integer>of());
        when(em.createQuery(startsWith("select p from PerfilUsuario"), eq(PerfilUsuario.class))).thenReturn(persona);
        when(em.createQuery(startsWith("select a from Actividad"), eq(Actividad.class))).thenReturn(activas);
        when(em.createQuery(startsWith("select e from EjecucionActividad"), eq(EjecucionActividad.class))).thenReturn(ejecuciones);
        when(em.createQuery(startsWith("select e.idEjecucionActividad"), eq(Integer.class))).thenReturn(historial);
        var diaHistorico = LocalDate.parse("2026-11-25");
        when(registro.inicio()).thenReturn(diaHistorico);
        when(registro.tieneRutinaHistorica(12, diaHistorico.plusDays(1))).thenReturn(true);
        var paso = new RegistroSeguimiento.PasoBase(7, 1, "Paso guardado", null, null, null);
        when(registro.delDia(12, diaHistorico)).thenReturn(List.of(
            new RegistroSeguimiento.Definicion(4, 2, "Actividad ya borrada", LocalTime.NOON, null, List.of(paso))));
        var reloj = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneId.of("America/Lima"));
        var servicio = new SeguimientoService(em, registro, reloj, "UTC");

        var pasado = servicio.consultar(3, 12, "2026-11-24");
        assertEquals(LocalDate.parse("2026-11-01"), pasado.fechaMinima());
        assertEquals("PARCIAL", pasado.cobertura());
        assertNull(pasado.resumen());
        var cubierto = servicio.consultar(3, 12, diaHistorico.toString());
        assertEquals(1, cubierto.resumen().pendientes());
        assertFalse(cubierto.tieneActividadesActivas());
        assertTrue(cubierto.tieneHistorial());
        assertEquals("Paso guardado", servicio.detalle(3, 12, 4, diaHistorico.toString()).pasos().getFirst().descripcion());
        assertThrows(RechazoActividad.class, () -> servicio.consultar(3, 12, "2026-10-31"));
    }

    @SuppressWarnings("unchecked")
    private static <T> TypedQuery<T> consulta(List<T> filas) {
        var query = (TypedQuery<T>) mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(filas);
        return query;
    }

    @Test void diaSinActividadesCareceDePorcentaje() {
        var resumen = SeguimientoService.resumir(List.of());
        assertEquals(0, resumen.total());
        assertNull(resumen.porcentaje());
    }

    @Test void porcentajeCuentaFinalizacionesYNoLimitaATen() {
        var filas = IntStream.rangeClosed(1, 12).mapToObj(i -> actividad(i,
            i <= 2 ? "COMPLETADA" : i == 3 ? "EN_PROGRESO" : "PENDIENTE")).toList();
        var resumen = SeguimientoService.resumir(filas);
        assertEquals(12, resumen.total());
        assertEquals(2, resumen.completadas());
        assertEquals(1, resumen.enProgreso());
        assertEquals(9, resumen.pendientes());
        assertEquals(17, resumen.porcentaje());
        assertEquals(0, SeguimientoService.resumir(List.of(actividad(1, "PENDIENTE"))).porcentaje());
    }

    private static SeguimientoService.ActividadDia actividad(int id, String estado) {
        return new SeguimientoService.ActividadDia(id, 1, "Actividad", "08:00", "bread", estado, 0, 3);
    }
}
