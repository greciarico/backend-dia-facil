package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EdicionActividadServiceTest {
    @Test void soloBorraFisicamenteSinEjecucionesProgresoNiAyuda() {
        for (boolean tieneAyuda : List.of(false, true)) {
            var em = mock(EntityManager.class);
            var perfiles = mock(PerfilUsuarioRepository.class);
            var perfil = mock(PerfilUsuario.class);
            var actividad = Actividad.crear(perfil, "Rutina", LocalTime.NOON, null);
            when(perfiles.bloquear(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
            var actividadQuery = consulta(List.of(actividad));
            var estadoQuery = consulta(List.<EstadoEjecucion>of());
            var vacia = consulta(List.<Integer>of());
            var ayuda = consulta(tieneAyuda ? List.of(42) : List.<Integer>of());
            when(vacia.setMaxResults(1)).thenReturn(vacia);
            when(ayuda.setMaxResults(1)).thenReturn(ayuda);
            when(em.createQuery(startsWith("select a from Actividad"), eq(Actividad.class))).thenReturn(actividadQuery);
            when(em.createQuery(startsWith("select e.estado"), eq(EstadoEjecucion.class))).thenReturn(estadoQuery);
            when(em.createQuery(startsWith("select e.idEjecucionActividad"), eq(Integer.class))).thenReturn(vacia);
            when(em.createQuery(startsWith("select g.idProgresoPaso"), eq(Integer.class))).thenReturn(vacia);
            when(em.createQuery(startsWith("select s.idSolicitudAyuda"), eq(Integer.class))).thenReturn(ayuda);
            var service = new EdicionActividadService(em, perfiles, null, null, null,
                mock(PlatformTransactionManager.class), Clock.systemUTC(), mock(RegistroSeguimiento.class));
            assertEquals("ACTIVIDAD_MODIFICADA", assertThrows(RechazoActividad.class, () -> service.eliminar(3, 12, 7, 2)).codigo());
            var result = service.eliminar(3, 12, 7, 1);
            assertEquals("PENDIENTE", result.estadoHoy());
            assertEquals(tieneAyuda ? "RETIRADA" : "BORRADA", result.resultado());
            if (tieneAyuda) assertEquals(EstadoActividad.INACTIVA, actividad.getEstado());
            verify(em, times(tieneAyuda ? 0 : 1)).remove(actividad);
        }
    }

    @Test void guardaSinCambiarIdentidadYRechazaUnaSegundaEdicionConLaRevisionAnterior() {
        var em = mock(EntityManager.class);
        var perfiles = mock(PerfilUsuarioRepository.class);
        var iconos = mock(IconoActividadRepository.class);
        var creacion = mock(CrearActividadService.class);
        var pasos = mock(CreacionActividadTransaccionalService.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneId.of("America/Lima"));
        var perfil = PerfilUsuario.crear(Cuidador.crear("c@example.invalid", "Cuidador"), "Persona", null, "DF-7KQ9W6H2M8V3X5R2");
        var icono = IconoActividad.crear("bread", "Pan");
        var actividad = Actividad.crear(perfil, "Antes", LocalTime.NOON, icono);
        ReflectionTestUtils.setField(actividad, "idActividad", 7);
        var anterior = PasoActividad.crear(actividad, 1, "Paso anterior", null);
        ReflectionTestUtils.setField(anterior, "idPasoActividad", 4);
        when(perfiles.bloquear(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        when(iconos.findByCodigo("bread")).thenReturn(Optional.of(icono));
        var actividadesQuery = consulta(List.of(actividad));
        var pasosQuery = consulta(List.of(anterior));
        var estadoQuery = consulta(List.of(EstadoEjecucion.COMPLETADA));
        when(em.createQuery(startsWith("select a from Actividad"), eq(Actividad.class))).thenReturn(actividadesQuery);
        when(em.createQuery(startsWith("select p from PasoActividad"), eq(PasoActividad.class))).thenReturn(pasosQuery);
        when(em.createQuery(startsWith("select e.estado"), eq(EstadoEjecucion.class))).thenReturn(estadoQuery);
        doAnswer(call -> {
            PasoActividad paso = call.getArgument(0);
            ReflectionTestUtils.setField(paso, "idPasoActividad", 5);
            return null;
        }).when(em).persist(isA(PasoActividad.class));
        var service = new EdicionActividadService(em, perfiles, iconos, creacion, pasos, manager, clock, mock(RegistroSeguimiento.class));
        var entrada = new EdicionActividadService.Entrada(1, "Después", "09:00", "bread",
            List.of(new EdicionActividadService.PasoEntrada("Paso nuevo", null, null)));

        var resultado = service.editar(3, 12, 7, entrada);
        assertEquals(7, resultado.idActividad());
        assertEquals(2, resultado.revision());
        assertEquals("COMPLETADA", resultado.estadoHoy());
        assertEquals("09:00", resultado.hora());
        assertEquals("Paso nuevo", resultado.pasos().getFirst().descripcion());
        assertEquals("Paso anterior", anterior.getDescripcion());
        assertEquals(1, anterior.getRevision());
        assertEquals("ACTIVIDAD_MODIFICADA", assertThrows(RechazoActividad.class,
            () -> service.editar(3, 12, 7, entrada)).codigo());
        var orden = inOrder(perfiles, em);
        orden.verify(perfiles).bloquear(12, 3, EstadoPerfil.ACTIVO);
        orden.verify(em).refresh(actividad, LockModeType.PESSIMISTIC_WRITE);
        orden.verify(em).persist(isA(VersionActividad.class));
        orden.verify(em).persist(isA(PasoActividad.class));
        verify(em, never()).remove(any());
    }

    @SuppressWarnings("unchecked")
    private static <T> TypedQuery<T> consulta(List<T> resultados) {
        var query = (TypedQuery<T>) mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(resultados);
        when(query.getResultStream()).thenAnswer(call -> resultados.stream());
        return query;
    }
}
