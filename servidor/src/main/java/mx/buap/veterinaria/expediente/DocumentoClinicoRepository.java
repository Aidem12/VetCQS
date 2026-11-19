package mx.buap.veterinaria.expediente;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface DocumentoClinicoRepository extends JpaRepository<DocumentoClinico, Integer> {
    /** Lista sin cargar el contenido binario. */
    @Query("""
        select new mx.buap.veterinaria.expediente.DocumentoResumen(d.id, d.nombreOriginal, d.tipoMime, d.tamanoBytes,
               d.sha256, d.creadoEn, u.nombre, e.id)
        from DocumentoClinico d join d.subidoPor u left join d.estudio e
        where d.mascota.id = :mascotaId order by d.creadoEn desc""")
    List<DocumentoResumen> resumen(Integer mascotaId);
}
