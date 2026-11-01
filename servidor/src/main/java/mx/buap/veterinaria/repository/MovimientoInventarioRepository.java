package mx.buap.veterinaria.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.MovimientoInventario;
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario,Integer> {
    List<MovimientoInventario> findByProductoIdOrderByFechaDesc(Integer productoId);
}
