package mx.buap.veterinaria.expediente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AntecedenteClinicoRepository extends JpaRepository<AntecedenteClinico, Integer> {
    List<AntecedenteClinico> findByMascotaIdOrderByVigenteDescCreadoEnDesc(Integer mascotaId);
}
