package dia_facil.backend.service.datosbase;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificadorEsquemaDatosBaseTest {
    @Test void requiereLaUniqueRealNoSoloElNombreOIndiceNoUnico() throws Exception {
        VerificadorEsquemaDatosBase.verificar(conexion("correcto"));
        for (String caso : new String[] {"ausente", "no-unico", "columnas"}) {
            assertTrue(assertThrows(IllegalStateException.class, () -> VerificadorEsquemaDatosBase.verificar(conexion(caso))).getMessage().contains("uq_plantilla_paso_orden"));
        }
    }
    private Connection conexion(String caso) throws Exception {
        var c = mock(Connection.class); var metadata = mock(DatabaseMetaData.class); when(c.getMetaData()).thenReturn(metadata);
        when(c.getCatalog()).thenReturn("diafacil_test");
        when(metadata.getIndexInfo(eq("diafacil_test"), isNull(), anyString(), eq(true), eq(false))).thenAnswer(invocation -> {
            var key = VerificadorEsquemaDatosBase.REQUERIDAS.stream().filter(k -> k.tabla().equals(invocation.getArgument(2))).findFirst().orElseThrow();
            boolean objetivo = key.tabla().equals("plantilla_paso"); var position = new AtomicInteger(-1); var rs = mock(ResultSet.class);
            when(rs.next()).thenAnswer(i -> position.incrementAndGet() < (objetivo && caso.equals("ausente") ? 0 : key.columnas().size()));
            when(rs.getString("INDEX_NAME")).thenReturn(key.nombre());
            when(rs.getString("COLUMN_NAME")).thenAnswer(i -> objetivo && caso.equals("columnas") ? "otra" : key.columnas().get(position.get()));
            when(rs.getBoolean("NON_UNIQUE")).thenReturn(objetivo && caso.equals("no-unico"));
            when(rs.getShort("ORDINAL_POSITION")).thenAnswer(i -> (short) (position.get() + 1)); return rs;
        });
        return c;
    }
}
