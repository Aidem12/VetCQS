package mx.buap.veterinaria.seguridad;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import mx.buap.veterinaria.comun.Reloj;

/**
 * Limita intentos fallidos de contraseña (inicio de sesión y autorizaciones de administrador).
 * Regla: 5 fallos dentro de 15 minutos bloquean esa nombre de usuario durante 5 minutos.
 * Se guarda en memoria: un reinicio del servidor limpia los contadores (aceptable para una sola sucursal).
 */
@Component
public class LimiteIntentos {
    public static final int MAX_FALLOS = 5;
    public static final Duration VENTANA = Duration.ofMinutes(15);
    public static final Duration BLOQUEO = Duration.ofMinutes(5);

    private final Reloj reloj;
    private final Map<String, Estado> estados = new ConcurrentHashMap<>();

    public LimiteIntentos(Reloj reloj) { this.reloj = reloj; }

    private static final class Estado {
        final Deque<LocalDateTime> fallos = new ArrayDeque<>();
        LocalDateTime bloqueadoHasta;
    }

    public static String clave(String ambito, String usuario, String ip) {
        return ambito + "|" + (usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT)) + "|" + (ip == null ? "" : ip);
    }

    /** Segundos que faltan para desbloquear, o 0 si se permite intentar. */
    public long segundosBloqueado(String clave) {
        Estado e = estados.get(clave);
        if (e == null) return 0;
        synchronized (e) {
            LocalDateTime ahora = reloj.ahora();
            if (e.bloqueadoHasta == null || !ahora.isBefore(e.bloqueadoHasta)) return 0;
            return Math.max(1, Duration.between(ahora, e.bloqueadoHasta).toSeconds());
        }
    }

    /** Registra un fallo y devuelve true si con él la clave quedó bloqueada. */
    public boolean fallo(String clave) {
        Estado e = estados.computeIfAbsent(clave, k -> new Estado());
        synchronized (e) {
            LocalDateTime ahora = reloj.ahora();
            while (!e.fallos.isEmpty() && e.fallos.peekFirst().isBefore(ahora.minus(VENTANA))) e.fallos.pollFirst();
            e.fallos.addLast(ahora);
            if (e.fallos.size() >= MAX_FALLOS) {
                e.bloqueadoHasta = ahora.plus(BLOQUEO);
                e.fallos.clear();
                return true;
            }
            return false;
        }
    }

    public void exito(String clave) { estados.remove(clave); }
}
