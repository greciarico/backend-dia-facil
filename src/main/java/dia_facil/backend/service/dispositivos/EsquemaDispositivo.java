package dia_facil.backend.service.dispositivos;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Read-only startup verification, not schema initialization or repair. */
public final class EsquemaDispositivo {
    private EsquemaDispositivo() {}
    public static void verificar(EntityManager em) {
        em.createQuery("select c from CredencialDispositivo c where 1=0").getResultList();
        em.unwrap(org.hibernate.Session.class).doWork(EsquemaDispositivo::verificar);
    }
    static void verificar(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        Map<String,List<String>> indexes = new HashMap<>();
        try(var rows=metadata.getIndexInfo(connection.getCatalog(),null,"credencial_dispositivo",true,false)) {
            while(rows.next()) {
                String name=rows.getString("INDEX_NAME"), column=rows.getString("COLUMN_NAME");
                if(name!=null && column!=null && !rows.getBoolean("NON_UNIQUE")) indexes.computeIfAbsent(name,k->new ArrayList<>()).add(column.toLowerCase(Locale.ROOT));
            }
        }
        if(!indexes.containsValue(List.of("token_hash")) || !indexes.containsValue(List.of("id_dispositivo")))
            throw new IllegalStateException("E3 requiere UNIQUE token_hash e id_dispositivo; revisa el esquema sin eliminar datos.");
        boolean fk=false;
        try(var rows=metadata.getImportedKeys(connection.getCatalog(),null,"credencial_dispositivo")) {
            while(rows.next()) if("id_dispositivo".equalsIgnoreCase(rows.getString("FKCOLUMN_NAME"))
                && "dispositivo".equalsIgnoreCase(rows.getString("PKTABLE_NAME")) && "id_dispositivo".equalsIgnoreCase(rows.getString("PKCOLUMN_NAME"))) fk=true;
        }
        if(!fk) throw new IllegalStateException("E3 requiere FK de credencial a dispositivo.");
    }
}
