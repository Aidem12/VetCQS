package mx.buap.veterinaria.agenda;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TipoCitaRepository extends JpaRepository<TipoCita, Integer> {
    List<TipoCita> findByActivoTrueOrderByNombre();
    List<TipoCita> findAllByOrderByNombre();
}
