package mx.buap.veterinaria.farmacia;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {
    List<Proveedor> findAllByOrderByNombre();
    Optional<Proveedor> findByNombreIgnoreCase(String nombre);
}
