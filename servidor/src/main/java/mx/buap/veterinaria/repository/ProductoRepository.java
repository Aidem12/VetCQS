package mx.buap.veterinaria.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.buap.veterinaria.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Optional<Producto> findByCodigoBarrasAndActivoTrue(String codigoBarras);

    List<Producto> findTop20ByNombreContainingIgnoreCaseAndActivoTrueOrderByNombre(String texto);
}
