package mx.buap.veterinaria.agenda;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CitaHistorialRepository extends JpaRepository<CitaHistorial, Integer> {
    List<CitaHistorial> findByCitaIdOrderByFechaAsc(Integer citaId);
}
