package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.enums.EstadoActividad;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.ActividadRepository;
import dia_facil.backend.service.perfiles.PerfilUsuarioService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActividadService {
    private final PerfilUsuarioService perfiles;
    private final ActividadRepository actividades;

    public ActividadService(PerfilUsuarioService perfiles, ActividadRepository actividades) {
        this.perfiles = perfiles;
        this.actividades = actividades;
    }

    @Transactional(readOnly = true)
    public List<ActividadResumen> consultar(Integer cuidadorAutenticado, Integer idPerfilUsuario) {
        // An unavailable profile is never confused with an empty activity list.
        perfiles.detalle(cuidadorAutenticado, idPerfilUsuario);
        return List.copyOf(actividades.consultar(idPerfilUsuario, cuidadorAutenticado, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA));
    }
}
