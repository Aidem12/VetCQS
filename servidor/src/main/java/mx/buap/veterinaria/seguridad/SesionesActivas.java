package mx.buap.veterinaria.seguridad;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import mx.buap.veterinaria.config.WebSocketConfig.AvisosCaja;

/**
 * Política de sesiones: una sesión activa por usuario. Un nuevo inicio de sesión cierra la anterior
 * (el portal comparte su misma sesión con Caja y Consultorio, así que no se ven afectados).
 * Cuando una sesión termina (logout, expiración o usuario desactivado) se cierran sus WebSocket.
 */
@Component
public class SesionesActivas implements HttpSessionListener {
    private final Map<Integer, HttpSession> porUsuario = new ConcurrentHashMap<>();
    private final AvisosCaja avisos;

    public SesionesActivas(AvisosCaja avisos) { this.avisos = avisos; }

    /** Registra la sesión del usuario; si tenía otra distinta, la invalida. Devuelve true si cerró una anterior. */
    public boolean registrar(Integer usuarioId, HttpSession nueva) {
        HttpSession anterior = porUsuario.put(usuarioId, nueva);
        if (anterior != null && !anterior.getId().equals(nueva.getId())) {
            try { anterior.invalidate(); } catch (IllegalStateException yaInvalida) { /* ya había expirado */ }
            return true;
        }
        return false;
    }

    public void quitar(Integer usuarioId, HttpSession sesion) {
        if (usuarioId != null) porUsuario.remove(usuarioId, sesion);
    }

    /** Tras rotar el id de sesión en el login se conserva el mismo objeto, por eso se compara por referencia. */
    @Override public void sessionDestroyed(HttpSessionEvent evento) {
        HttpSession s = evento.getSession();
        porUsuario.values().removeIf(x -> x == s);
        avisos.cerrarSesion(s.getId());
    }
}
