package mx.buap.veterinaria.expediente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotaClinicaRepository extends JpaRepository<NotaClinica, Integer> {
    List<NotaClinica> findByConsultaMascotaIdOrderByCreadoEnAsc(Integer mascotaId);
}
