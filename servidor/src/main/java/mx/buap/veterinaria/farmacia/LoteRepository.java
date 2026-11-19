package mx.buap.veterinaria.farmacia;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface LoteRepository extends JpaRepository<Lote, Integer> {
    /** Lotes con existencia de un producto en orden FEFO (primero el que vence antes), bloqueados para actualizar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lote l where l.producto.id = :productoId and l.cantidad > 0 order by l.caducidad, l.id")
    List<Lote> paraConsumir(Integer productoId);

    @Query("select coalesce(sum(l.cantidad), 0) from Lote l where l.producto.id = :productoId and l.caducidad >= :hoy")
    java.math.BigDecimal disponibleVigente(Integer productoId, LocalDate hoy);

    @Query("""
        select l from Lote l join fetch l.producto p left join fetch l.proveedor pr
        where (cast(:productoId as Integer) is null or p.id = :productoId)
          and (cast(:proveedorId as Integer) is null or pr.id = :proveedorId)
          and (cast(:caducaAntes as LocalDate) is null or l.caducidad <= :caducaAntes)
          and (:conExistencia = false or l.cantidad > 0)
        order by l.caducidad, p.nombre, l.id""")
    List<Lote> consultar(Integer productoId, Integer proveedorId, LocalDate caducaAntes, boolean conExistencia);

    Optional<Lote> findByProductoIdAndNumeroLoteIgnoreCase(Integer productoId, String numeroLote);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lote l where l.id = :id")
    Optional<Lote> findByIdForUpdate(Integer id);
}
