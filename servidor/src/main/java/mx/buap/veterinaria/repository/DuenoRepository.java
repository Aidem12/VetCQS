package mx.buap.veterinaria.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.Dueno;

public interface DuenoRepository extends JpaRepository<Dueno, Integer> {
    List<Dueno> findTop20ByNombreContainingIgnoreCaseOrTelefonoContainingOrderByNombre(String nombre, String telefono);
}
