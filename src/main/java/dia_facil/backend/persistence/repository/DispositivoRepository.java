package dia_facil.backend.persistence.repository;

import dia_facil.backend.persistence.entity.Dispositivo;
import dia_facil.backend.persistence.enums.EstadoDispositivo;
import org.springframework.data.repository.Repository;

public interface DispositivoRepository extends Repository<Dispositivo, Integer> {
    long countByPerfilUsuario_IdPerfilUsuarioAndEstado(Integer perfil, EstadoDispositivo estado);
}
