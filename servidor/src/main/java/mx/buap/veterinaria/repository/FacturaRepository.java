package mx.buap.veterinaria.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.Factura;
public interface FacturaRepository extends JpaRepository<Factura,Integer> { Optional<Factura> findByIdempotencia(String idempotencia); Optional<Factura> findByVentaId(Integer ventaId); }
