package mx.buap.veterinaria.comun;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import mx.buap.veterinaria.model.Bitacora;
import mx.buap.veterinaria.repository.BitacoraRepository;

/** Registro de auditoría de cambios y accesos sobre la tabla bitácora existente. Nunca guarda contraseñas. */
@Service
public class AuditoriaService {
    private final BitacoraRepository bitacora;
    private Reloj reloj = new Reloj();
    public AuditoriaService(BitacoraRepository bitacora) { this.bitacora = bitacora; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setReloj(Reloj reloj) { this.reloj = reloj; }

    @Transactional
    public void registrar(Integer usuarioId, String accion, String tabla, Integer registroId, String detalle) {
        if (usuarioId == null) return;
        Bitacora b = new Bitacora(usuarioId, null, recortar(accion, 40), recortar(tabla, 40),
                registroId == null ? 0 : registroId, recortar(detalle, 2000));
        b.setFecha(reloj.ahora());
        bitacora.save(b);
    }

    /**
     * Eventos de acceso (LOGIN_OK, LOGIN_FALLIDO, LOGIN_BLOQUEADO, LOGOUT, SESION_REEMPLAZADA...).
     * Va en su propia transacción para que quede registrado aunque la operación principal falle.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acceso(Integer usuarioId, String usuarioTexto, String accion, String ip, String detalle) {
        Bitacora b = new Bitacora(usuarioId, null, recortar(accion, 40), "acceso",
                usuarioId == null ? 0 : usuarioId, recortar(detalle, 500));
        b.setUsuarioTexto(recortar(usuarioTexto, 40));
        b.setIp(recortar(ip, 45));
        b.setFecha(reloj.ahora());
        bitacora.save(b);
    }

    private static String recortar(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max); }

    /** Dirección del equipo que hizo la petición (no se confía en cabeceras de proxy). */
    public static String ip(jakarta.servlet.http.HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    LocalDateTime ahora() { return reloj.ahora(); }
}
