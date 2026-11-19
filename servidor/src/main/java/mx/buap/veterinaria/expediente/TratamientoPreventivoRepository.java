package mx.buap.veterinaria.expediente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TratamientoPreventivoRepository extends JpaRepository<TratamientoPreventivo, Integer> {
    List<TratamientoPreventivo> findByMascotaIdOrderByFechaAplicacionDesc(Integer mascotaId);
}
