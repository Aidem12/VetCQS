package mx.buap.veterinaria.agenda;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface AusenciaVeterinarioRepository extends JpaRepository<AusenciaVeterinario, Integer> {
    @Query("select a from AusenciaVeterinario a where a.veterinario.id = :vet and a.inicio < :hasta and a.fin > :desde order by a.inicio")
    List<AusenciaVeterinario> traslapadas(Integer vet, LocalDateTime desde, LocalDateTime hasta);
    List<AusenciaVeterinario> findByVeterinarioIdAndFinAfterOrderByInicio(Integer vet, LocalDateTime desde);
}
