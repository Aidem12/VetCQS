package mx.buap.veterinaria.expediente;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface VacunaRepository extends JpaRepository<Vacuna, Integer> {
    List<Vacuna> findByMascotaIdOrderByFechaAplicacionDesc(Integer mascotaId);
    @Query("select v from Vacuna v join fetch v.mascota m join fetch m.dueno where v.anulada = false and v.proximoRefuerzo between :desde and :hasta order by v.proximoRefuerzo")
    List<Vacuna> refuerzosEntre(LocalDate desde, LocalDate hasta);
}
