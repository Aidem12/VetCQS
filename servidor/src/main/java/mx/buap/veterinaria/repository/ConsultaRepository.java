package mx.buap.veterinaria.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.buap.veterinaria.model.Consulta;

public interface ConsultaRepository extends JpaRepository<Consulta, Integer> {
    List<Consulta> findByMascotaIdOrderByFechaDesc(Integer mascotaId);
}
