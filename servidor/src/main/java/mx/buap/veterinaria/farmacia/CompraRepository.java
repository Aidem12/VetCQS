package mx.buap.veterinaria.farmacia;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface CompraRepository extends JpaRepository<Compra, Integer> {
    @Query("select c from Compra c join fetch c.proveedor join fetch c.usuario order by c.fecha desc")
    List<Compra> recientes(org.springframework.data.domain.Pageable pagina);
}
