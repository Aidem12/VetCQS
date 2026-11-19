package mx.buap.veterinaria.expediente;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface RecetaRepository extends JpaRepository<Receta, Integer> {
    List<Receta> findByMascotaIdOrderByFechaDesc(Integer mascotaId);
    @Query(value = "select nextval('receta_folio_seq')", nativeQuery = true)
    long siguienteFolio();
}
