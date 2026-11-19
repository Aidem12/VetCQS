package mx.buap.veterinaria.seguridad;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import mx.buap.veterinaria.model.Bitacora;

/** Consulta de la bitácora para el administrador (solo lectura; protegido en SecurityConfig). */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {
    private final EntityManager em;
    public AuditoriaController(EntityManager em) { this.em = em; }

    public record Registro(Integer id, String fecha, Integer usuarioId, String usuario, Integer autorizoId, String accion,
            String tabla, Integer registroId, String detalle, String ip) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<Registro> buscar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tabla, @RequestParam(required = false) String accion,
            @RequestParam(required = false) Integer usuarioId) {
        if (hasta.isBefore(desde) || desde.plusDays(93).isBefore(hasta))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique un rango de hasta 3 meses.");
        StringBuilder jpql = new StringBuilder("select b from Bitacora b where b.fecha >= :desde and b.fecha < :hasta");
        if (tabla != null && !tabla.isBlank()) jpql.append(" and b.tabla = :tabla");
        if (accion != null && !accion.isBlank()) jpql.append(" and b.accion = :accion");
        if (usuarioId != null) jpql.append(" and b.usuarioId = :usuario");
        jpql.append(" order by b.id desc");
        var q = em.createQuery(jpql.toString(), Bitacora.class)
                .setParameter("desde", desde.atStartOfDay()).setParameter("hasta", hasta.plusDays(1).atStartOfDay())
                .setMaxResults(500);
        if (tabla != null && !tabla.isBlank()) q.setParameter("tabla", tabla.trim());
        if (accion != null && !accion.isBlank()) q.setParameter("accion", accion.trim());
        if (usuarioId != null) q.setParameter("usuario", usuarioId);
        return q.getResultList().stream().map(b -> new Registro(b.getId(), b.getFecha().toString(), b.getUsuarioId(),
                b.getUsuarioTexto(), b.getAutorizoId(), b.getAccion(), b.getTabla(), b.getRegistroId(), b.getDetalle(), b.getIp())).toList();
    }
}
