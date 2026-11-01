package mx.buap.veterinaria.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import mx.buap.veterinaria.model.Turno;

public interface TurnoRepository extends JpaRepository<Turno, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.cierre is null")
    Optional<Turno> findOpenForUpdate();
    Optional<Turno> findByCierreIsNull();
}
