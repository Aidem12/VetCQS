package mx.buap.veterinaria.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.Mascota;

public interface MascotaRepository extends JpaRepository<Mascota, Integer> {
    List<Mascota> findByDuenoIdOrderByNombre(Integer duenoId);
}
