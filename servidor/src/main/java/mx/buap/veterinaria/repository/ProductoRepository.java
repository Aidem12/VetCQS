package mx.buap.veterinaria.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import mx.buap.veterinaria.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Optional<Producto> findByCodigoBarrasAndActivoTrue(String codigoBarras);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Producto p where p.id = :id")
    Optional<Producto> findByIdForUpdate(Integer id);

    List<Producto> findTop20ByNombreContainingIgnoreCaseAndActivoTrueOrderByNombre(String texto);
}
