package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import java.util.*;

/** A reached prefix, with at most its frontier awaiting confirmation. No persisted maximum. */
final class RecorridoGuia {
    final List<PasoActividad> pasos;
    final Map<Integer,ProgresoPaso> progreso=new HashMap<>();
    final int maximo;
    RecorridoGuia(List<PasoActividad> pasos,List<ProgresoPaso> registros) {
        this.pasos=List.copyOf(pasos);
        if(pasos.isEmpty())throw RechazoGuia.noDisponible();
        int orden=0;
        var ids=new HashSet<Integer>();
        for(var p:pasos){if(p.getOrden()==null||p.getOrden()<=orden||!ids.add(p.getIdPasoActividad()))throw RechazoGuia.noDisponible();orden=p.getOrden();}
        for(var p:registros)if(!ids.contains(p.getPasoActividad().getIdPasoActividad())||progreso.put(p.getPasoActividad().getIdPasoActividad(),p)!=null)throw RechazoGuia.noDisponible();
        int frontera=-1;boolean cerrado=false;
        for(int i=0;i<pasos.size();i++) {
            var p=progreso.get(pasos.get(i).getIdPasoActividad());
            if(p==null){cerrado=true;continue;}
            if(cerrado)throw RechazoGuia.noDisponible();
            frontera=i;
            if(!Boolean.TRUE.equals(p.getCompletado()))cerrado=true;
        }
        maximo=frontera;
    }
    int posicion(Integer id) {
        for(int i=0;i<pasos.size();i++)if(pasos.get(i).getIdPasoActividad().equals(id))return i;
        throw RechazoGuia.paso();
    }
    boolean completoAntesDelUltimo() {
        return maximo==pasos.size()-1 && pasos.subList(0,pasos.size()-1).stream()
            .allMatch(p->Boolean.TRUE.equals(progreso.get(p.getIdPasoActividad()).getCompletado()));
    }
}
