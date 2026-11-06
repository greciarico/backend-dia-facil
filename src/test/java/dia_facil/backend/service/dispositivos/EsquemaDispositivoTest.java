package dia_facil.backend.service.dispositivos;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EsquemaDispositivoTest {
    @Test void exigeAmbosUniqueYFkSinEjecutarDdl() throws Exception {
        var connection=mock(Connection.class);var meta=mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(meta);when(connection.getCatalog()).thenReturn("test");
        var indexes=mock(ResultSet.class);var fks=mock(ResultSet.class);
        when(meta.getIndexInfo("test",null,"credencial_dispositivo",true,false)).thenReturn(indexes);
        when(meta.getImportedKeys("test",null,"credencial_dispositivo")).thenReturn(fks);
        when(indexes.next()).thenReturn(true,true,false);
        when(indexes.getString("INDEX_NAME")).thenReturn("token","device");
        when(indexes.getString("COLUMN_NAME")).thenReturn("token_hash","id_dispositivo");
        when(fks.next()).thenReturn(true,false);when(fks.getString("FKCOLUMN_NAME")).thenReturn("id_dispositivo");
        when(fks.getString("PKTABLE_NAME")).thenReturn("dispositivo");when(fks.getString("PKCOLUMN_NAME")).thenReturn("id_dispositivo");
        assertDoesNotThrow(()->EsquemaDispositivo.verificar(connection));
        when(indexes.next()).thenReturn(true,false);when(indexes.getString("COLUMN_NAME")).thenReturn("token_hash");
        assertThrows(IllegalStateException.class,()->EsquemaDispositivo.verificar(connection));
        verify(connection,never()).createStatement();verify(connection,never()).prepareStatement(anyString());
    }
}
