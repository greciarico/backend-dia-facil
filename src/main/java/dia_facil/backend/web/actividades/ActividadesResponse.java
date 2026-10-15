package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.ActividadResumen;
import java.util.List;

public record ActividadesResponse(List<ActividadResumen> actividades) {}
