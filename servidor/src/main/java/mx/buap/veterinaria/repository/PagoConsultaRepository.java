package mx.buap.veterinaria.repository;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import mx.buap.veterinaria.model.Pago;

public interface PagoConsultaRepository extends JpaRepository<Pago, Integer> {
    @Query("select coalesce(sum(p.monto),0) from Pago p where p.venta.turno.id = :turno and p.forma = mx.buap.veterinaria.model.Pago.Forma.EFECTIVO")
    BigDecimal efectivoDelTurno(@Param("turno") Integer turno);
}
