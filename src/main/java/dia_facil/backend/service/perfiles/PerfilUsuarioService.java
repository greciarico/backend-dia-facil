package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.PerfilUsuarioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilUsuarioService {
    private final PerfilUsuarioRepository perfiles;
    public PerfilUsuarioService(PerfilUsuarioRepository perfiles) { this.perfiles = perfiles; }

    @Transactional(readOnly = true)
    public List<PerfilResumen> consultar(Integer cuidadorAutenticado) {
        return List.copyOf(perfiles.consultar(cuidadorAutenticado, EstadoPerfil.ACTIVO));
    }
}
