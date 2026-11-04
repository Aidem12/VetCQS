package mx.buap.veterinaria.repository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import mx.buap.veterinaria.model.Venta;
public interface VentaRepository extends JpaRepository<Venta,Integer> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from Venta v where v.id=:id")
 Optional<Venta> findByIdForUpdate(@Param("id") Integer id);
 List<Venta> findTop100ByEstadoInOrderByFechaAsc(Collection<Venta.Estado> estados);
 Optional<Venta> findByConsultaId(Integer consultaId);
 List<Venta> findByTurnoIdOrderByFecha(Integer turnoId);
}
