package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.perfiles.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrearActividadServiceTest {
    private static CrearActividad datos(Integer id) { return new CrearActividad("  Desayuno  ", "00:00", "bread", List.of(new CrearActividad.Paso("Texto final", id))); }
    @Test void validaContratoSinUnMaximoFuncionalNiCambiarDescripciones() {
        var validos = datos(null).validado(); assertEquals("Desayuno", validos.nombre()); assertEquals("00:00", validos.horaLocal().toString());
        var muchos = new CrearActividad("A", "23:59", "bread", Collections.nCopies(65536, new CrearActividad.Paso("  Texto con espacios  ", null))).validado();
        assertEquals(65536, muchos.pasos().size()); assertEquals("  Texto con espacios  ", muchos.pasos().getFirst().descripcion());
        for (var invalido : List.of(new CrearActividad(" ", "00:00", "bread", validos.pasos()),
                new CrearActividad("a".repeat(81), "00:00", "bread", validos.pasos()),
                new CrearActividad("A", "24:00", "bread", validos.pasos()), new CrearActividad("A", "7:00", "bread", validos.pasos()),
                new CrearActividad("A", "07:00:00", "bread", validos.pasos()), new CrearActividad("A", "00:00", null, validos.pasos()),
                new CrearActividad("A", "00:00", "bread", List.of()),
                new CrearActividad("A", "00:00", "bread", List.of(new CrearActividad.Paso(" ", null))),
                new CrearActividad("A", "00:00", "bread", List.of(new CrearActividad.Paso("a".repeat(121), null))), datos(-1)))
            assertThrows(RechazoActividad.class, invalido::validado);
    }
    record Fixture(PerfilUsuarioService perfiles, IconoActividadRepository iconos, PictogramaRepository pictogramas,
            PictogramaArasaacService arasaac, CreacionActividadTransaccionalService tx, CrearActividadService service) {}
    private Fixture fixture() {
        var perfiles = mock(PerfilUsuarioService.class); var iconos = mock(IconoActividadRepository.class);
        var pictos = mock(PictogramaRepository.class); var arasaac = mock(PictogramaArasaacService.class);
        var tx = mock(CreacionActividadTransaccionalService.class);
        when(iconos.findByCodigo("bread")).thenReturn(Optional.of(IconoActividad.crear("bread", "Pan")));
        return new Fixture(perfiles, iconos, pictos, arasaac, tx, new CrearActividadService(perfiles, iconos, pictos, arasaac, tx));
    }
    @Test void propietarioSeValidaAntesDeConsultarProveedorOEscribir() {
        var f = fixture(); when(f.perfiles.detalle(3, 12)).thenThrow(RechazoPerfil.noEncontrado());
        assertThrows(RechazoPerfil.class, () -> f.service.crear(3, 12, datos(2443)));
        verifyNoInteractions(f.arasaac, f.tx, f.pictogramas);
    }
    @Test void referenciasPersistidasNoConsultanArasaacYNuevasSoloUnaVezPorId() {
        var f = fixture(); var existente = Pictograma.crearArasaac("Etiqueta curada", 2443);
        when(f.pictogramas.findByIdArasaacIn(any())).thenReturn(List.of(existente));
        f.service.crear(3, 12, datos(2443)); verifyNoInteractions(f.arasaac);
        verify(f.tx).crear(eq(3), eq(12), any(), eq(Map.of()));
        when(f.pictogramas.findByIdArasaacIn(any())).thenReturn(List.of());
        when(f.arasaac.resolver(123)).thenReturn(PictogramaArasaac.de(123, "Proveedor"));
        f.service.crear(3, 12, new CrearActividad("A", "12:00", "bread", Collections.nCopies(3, new CrearActividad.Paso("Paso", 123))));
        verify(f.arasaac, times(1)).resolver(123);
    }
    @Test void iconoInexistenteOMetadataNoDisponibleNoIniciaEscritura() {
        var f = fixture(); when(f.iconos.findByCodigo("bread")).thenReturn(Optional.empty());
        assertEquals("ICONO_NO_ENCONTRADO", assertThrows(RechazoActividad.class, () -> f.service.crear(3,12,datos(null))).codigo());
        verifyNoInteractions(f.tx, f.arasaac);
        when(f.iconos.findByCodigo("bread")).thenReturn(Optional.of(IconoActividad.crear("bread","Pan")));
        when(f.arasaac.resolver(123)).thenThrow(new ArasaacClient.NoDisponible());
        assertThrows(ArasaacClient.NoDisponible.class, () -> f.service.crear(3,12,datos(123))); verifyNoInteractions(f.tx);
    }
    @Test void reintentaSoloLaColisionIdentificadaDespuesDelRollback() {
        var f = fixture(); var colision = new org.hibernate.exception.ConstraintViolationException("reserved", new java.sql.SQLException(), "uq_pictograma_arasaac");
        when(f.tx.crear(any(),any(),any(),any())).thenThrow(colision).thenReturn(new ActividadResumen(7,"A","00:00","bread"));
        assertEquals(7,f.service.crear(3,12,datos(null)).idActividad()); verify(f.tx,times(2)).crear(any(),any(),any(),any());
        doThrow(new IllegalStateException()).when(f.tx).crear(any(),any(),any(),any());
        assertThrows(IllegalStateException.class, () -> f.service.crear(3,12,datos(null)));
        doThrow(colision).when(f.tx).crear(any(),any(),any(),any());
        assertEquals("CONFLICTO_CREACION", assertThrows(RechazoActividad.class, () -> f.service.crear(3,12,datos(null))).codigo());
    }
    @Test void persisteDefinicionYPasosPropiosEnOrdenSinTocarPlantillas() {
        var perfiles = mock(PerfilUsuarioRepository.class); var iconos = mock(IconoActividadRepository.class); var pictos = mock(PictogramaRepository.class);
        var em = mock(EntityManager.class); var perfil = PerfilUsuario.crear(null,"Persona",null,"DF-000001");
        when(perfiles.findByIdPerfilUsuarioAndIdCuidadorAndEstado(12,3,EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        when(iconos.findByCodigo("bread")).thenReturn(Optional.of(IconoActividad.crear("bread","Pan")));
        var escrituras = new ArrayList<Object>(); doAnswer(call -> { var entity = call.getArgument(0); escrituras.add(entity);
            if (entity instanceof Actividad a) ReflectionTestUtils.setField(a,"idActividad",27); return null;
        }).when(em).persist(any());
        var service = new CreacionActividadTransaccionalService(perfiles,iconos,pictos,em);
        var entrada = new CrearActividad("Desayuno","07:00","bread",List.of(new CrearActividad.Paso("Primero",123),new CrearActividad.Paso("Segundo",null))).validado();
        var resultado = service.crear(3,12,entrada,Map.of(123,PictogramaArasaac.de(123,"Oficial")));
        assertEquals(27,resultado.idActividad()); assertEquals(4,escrituras.size());
        var pic = (Pictograma)escrituras.get(0); assertEquals("ARASAAC",pic.getFuente()); assertNull(pic.getUrlImagen());
        var actividad = (Actividad)escrituras.get(1); assertSame(perfil,actividad.getPerfilUsuario()); assertEquals(EstadoActividad.ACTIVA,actividad.getEstado()); assertFalse(actividad.getGuiaInicialCompletada());
        for (int i=0;i<2;i++) { var paso=(PasoActividad)escrituras.get(i+2); assertEquals(i+1,paso.getOrden()); assertTrue(paso.getActivo()); assertSame(actividad,paso.getActividad()); }
        assertSame(pic,((PasoActividad)escrituras.get(2)).getPictograma()); assertNull(((PasoActividad)escrituras.get(3)).getPictograma()); verify(em).flush();
    }
}
