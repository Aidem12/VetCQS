package mx.buap.veterinaria.expediente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EstudioRepository extends JpaRepository<Estudio, Integer> {
    List<Estudio> findByMascotaIdOrderByFechaDesc(Integer mascotaId);
}
