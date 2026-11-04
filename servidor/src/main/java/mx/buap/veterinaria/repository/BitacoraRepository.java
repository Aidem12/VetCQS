package mx.buap.veterinaria.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.Bitacora;
public interface BitacoraRepository extends JpaRepository<Bitacora,Integer> {
 Optional<Bitacora> findTopByTablaAndRegistroIdAndAccionOrderByIdDesc(String tabla,Integer registroId,String accion);
}
