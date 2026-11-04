package mx.buap.veterinaria.repository;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import mx.buap.veterinaria.model.Pago;
public interface PagoConsultaRepository extends JpaRepository<Pago,Integer> {
 @Query("select coalesce(sum(p.monto),0) from Pago p where p.venta.turno.id=:turno and p.forma=:forma and p.venta.estado=mx.buap.veterinaria.model.Venta.Estado.PAGADA")
 BigDecimal totalDelTurno(@Param("turno") Integer turno,@Param("forma") Pago.Forma forma);
}
