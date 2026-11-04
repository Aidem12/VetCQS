package mx.buap.veterinaria.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.SalidaEfectivo;
public interface SalidaEfectivoRepository extends JpaRepository<SalidaEfectivo,Integer> {
 List<SalidaEfectivo> findByTurnoIdOrderByFecha(Integer turnoId);
}
