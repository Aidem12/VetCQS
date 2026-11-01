package mx.buap.veterinaria.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    java.util.Optional<Usuario> findByUsuarioAndActivoTrue(String usuario);
    Optional<Usuario> findByUsuario(String usuario);
    Optional<Usuario> findFirstByRolAndActivoTrue(Usuario.Rol rol);
}
