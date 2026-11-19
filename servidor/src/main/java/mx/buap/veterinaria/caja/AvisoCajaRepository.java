package mx.buap.veterinaria.caja;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvisoCajaRepository extends JpaRepository<AvisoCaja, Integer> {
    List<AvisoCaja> findTop50ByIdGreaterThanOrderByIdAsc(Integer id);
    List<AvisoCaja> findTop20ByOrderByIdDesc();
}
