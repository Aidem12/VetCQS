package mx.buap.veterinaria.agenda;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface CitaRepository extends JpaRepository<Cita, Integer> {
    @Query("""
        select c from Cita c join fetch c.mascota m join fetch m.dueno d join fetch c.veterinario v join fetch c.tipo t
        where c.inicio < :hasta and c.fin > :desde
          and (:vet is null or v.id = :vet)
          and (:mascota is null or m.id = :mascota)
          and (:dueno is null or d.id = :dueno)
          and (:estado is null or c.estado = :estado)
          and (:texto = '' or lower(m.nombre) like concat('%', :texto, '%') or lower(d.nombre) like concat('%', :texto, '%'))
        order by c.inicio, v.nombre""")
    List<Cita> buscar(LocalDateTime desde, LocalDateTime hasta, Integer vet, Integer mascota, Integer dueno,
            Cita.Estado estado, String texto);

    /** Citas activas del veterinario que se cruzan con [desde, hasta), excepto la indicada. */
    @Query("""
        select count(c) from Cita c where c.veterinario.id = :vet and c.inicio < :hasta and c.fin > :desde
          and c.estado not in (mx.buap.veterinaria.agenda.Cita.Estado.CANCELADA, mx.buap.veterinaria.agenda.Cita.Estado.NO_ASISTIO)
          and (:excluir is null or c.id <> :excluir)""")
    long traslapes(Integer vet, LocalDateTime desde, LocalDateTime hasta, Integer excluir);

    @Query("""
        select c from Cita c where c.veterinario.id = :vet and c.inicio < :hasta and c.fin > :desde
          and c.estado not in (mx.buap.veterinaria.agenda.Cita.Estado.CANCELADA, mx.buap.veterinaria.agenda.Cita.Estado.NO_ASISTIO)
        order by c.inicio""")
    List<Cita> ocupadas(Integer vet, LocalDateTime desde, LocalDateTime hasta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cita c where c.id = :id")
    Optional<Cita> findByIdForUpdate(Integer id);

    @Query("""
        select c from Cita c join fetch c.mascota m join fetch m.dueno join fetch c.veterinario join fetch c.tipo
        where c.inicio >= :desde and c.inicio < :hasta
          and c.estado in (mx.buap.veterinaria.agenda.Cita.Estado.PENDIENTE, mx.buap.veterinaria.agenda.Cita.Estado.CONFIRMADA)
          and (:vet is null or c.veterinario.id = :vet)
        order by c.inicio""")
    List<Cita> proximas(LocalDateTime desde, LocalDateTime hasta, Integer vet);
}
