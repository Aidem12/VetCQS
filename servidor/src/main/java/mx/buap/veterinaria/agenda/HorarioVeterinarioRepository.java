package mx.buap.veterinaria.agenda;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HorarioVeterinarioRepository extends JpaRepository<HorarioVeterinario, Integer> {
    List<HorarioVeterinario> findByVeterinarioIdOrderByDiaSemanaAscHoraInicioAsc(Integer veterinarioId);
    List<HorarioVeterinario> findByVeterinarioIdAndDiaSemanaOrderByHoraInicio(Integer veterinarioId, short diaSemana);
}
