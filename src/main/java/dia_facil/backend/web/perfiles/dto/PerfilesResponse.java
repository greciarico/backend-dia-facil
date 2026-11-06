package dia_facil.backend.web.perfiles.dto;

import dia_facil.backend.service.perfiles.PerfilResumen;
import java.util.List;

public record PerfilesResponse(List<PerfilResumen> perfiles, int maximoPerfilesActivos) {}
