package mx.buap.veterinaria.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findFirstByRolAndActivoTrue(Usuario.Rol rol);
}
