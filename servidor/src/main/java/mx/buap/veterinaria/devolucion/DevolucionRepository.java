package mx.buap.veterinaria.devolucion;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface DevolucionRepository extends JpaRepository<Devolucion, Integer> {
    @EntityGraph(attributePaths = {"detalles", "detalles.producto"})
    List<Devolucion> findByVentaIdOrderByIdAsc(Integer ventaId);

    @Query("select coalesce(sum(d.cantidad), 0) from DevolucionDetalle d where d.devolucion.ventaId = :ventaId and d.producto.id = :productoId")
    BigDecimal devuelto(Integer ventaId, Integer productoId);

    @Query("select coalesce(sum(d.monto), 0) from Devolucion d where d.ventaId = :ventaId")
    BigDecimal reembolsado(Integer ventaId);

    @Query("select coalesce(sum(d.monto), 0) from Devolucion d where d.turnoId = :turnoId and d.formaReembolso = mx.buap.veterinaria.devolucion.Devolucion.Forma.EFECTIVO")
    BigDecimal efectivoDelTurno(Integer turnoId);

    @Query(value = "select nextval('devolucion_folio_seq')", nativeQuery = true)
    Long siguienteFolio();
}
