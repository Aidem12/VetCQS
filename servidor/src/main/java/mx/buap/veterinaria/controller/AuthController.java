package mx.buap.veterinaria.controller;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import jakarta.validation.Valid;
import mx.buap.veterinaria.comun.AuditoriaService;
import mx.buap.veterinaria.comun.Reloj;
import mx.buap.veterinaria.dto.LoginRequest;
import mx.buap.veterinaria.repository.UsuarioRepository;
import mx.buap.veterinaria.seguridad.LimiteIntentos;
import mx.buap.veterinaria.seguridad.SesionesActivas;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UsuarioRepository usuarios; private final PasswordEncoder encoder;
    private final LimiteIntentos limite; private final SesionesActivas sesiones; private final AuditoriaService auditoria;

    /** Constructor para pruebas unitarias: sin auditoría ni registro de sesiones. */
    public AuthController(UsuarioRepository u, PasswordEncoder e) { this(u, e, new LimiteIntentos(new Reloj()), null, null); }

    @Autowired
    public AuthController(UsuarioRepository u, PasswordEncoder e, LimiteIntentos limite, SesionesActivas sesiones,
            AuditoriaService auditoria) {
        usuarios=u; encoder=e; this.limite=limite; this.sesiones=sesiones; this.auditoria=auditoria;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest r, HttpSession session,
            HttpServletRequest request) {
        String ip = AuditoriaService.ip(request);
        String clave = LimiteIntentos.clave("login", r.usuario(), null);
        long espera = limite.segundosBloqueado(clave);
        if (espera > 0) {
            limpiar(session);
            auditar(null, r.usuario(), "LOGIN_BLOQUEADO", ip, "Intento durante bloqueo temporal");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, String.valueOf(espera))
                .body(Map.of("error", "Demasiados intentos fallidos. Intente de nuevo en " + minutos(espera) + "."));
        }
        var encontrado = usuarios.findByUsuarioAndActivoTrue(r.usuario());
        return encontrado
            .filter(u -> encoder.matches(r.contrasena(), u.getContrasenaHash()))
            .<ResponseEntity<?>>map(u -> {
                limite.exito(clave);
                request.changeSessionId();
                session.setAttribute("usuarioId", u.getId());
                session.setAttribute("rol", u.getRol().name());
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(u.getId().toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name()))));
                boolean reemplazo = sesiones != null && sesiones.registrar(u.getId(), session);
                auditar(u.getId(), u.getUsuario(), "LOGIN_OK", ip, reemplazo ? "Cerró la sesión anterior del usuario" : null);
                return ResponseEntity.ok(Map.of("id",u.getId(),"nombre",u.getNombre(),"usuario",u.getUsuario(),"rol",u.getRol()));
            })
            .orElseGet(() -> {
                limpiar(session);
                boolean bloqueado = limite.fallo(clave);
                auditar(encontrado.map(x -> x.getId()).orElse(null), r.usuario(), bloqueado ? "LOGIN_BLOQUEADO" : "LOGIN_FALLIDO", ip,
                        bloqueado ? "Se alcanzó el límite de intentos" : null);
                if (bloqueado) return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(LimiteIntentos.BLOQUEO.toSeconds()))
                        .body(Map.of("error", "Demasiados intentos fallidos. Intente de nuevo en 5 minutos."));
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Credenciales inválidas"));
            });
    }
    @GetMapping("/sesion")
    public ResponseEntity<?> sesion(HttpSession session) {
        Object id=session.getAttribute("usuarioId");
        if (!(id instanceof Integer usuarioId)) return ResponseEntity.status(401).build();
        return usuarios.findById(usuarioId).filter(mx.buap.veterinaria.model.Usuario::isActivo)
            .<ResponseEntity<?>>map(u -> ResponseEntity.ok(Map.of("id",u.getId(),"nombre",u.getNombre(),"rol",u.getRol().name())))
            .orElseGet(() -> ResponseEntity.status(401).build());
    }
    public ResponseEntity<Void> logout(HttpSession session) { return logout(session, null); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session, HttpServletRequest request) {
        Object id = session.getAttribute("usuarioId");
        if (id instanceof Integer usuarioId) {
            if (sesiones != null) sesiones.quitar(usuarioId, session);
            auditar(usuarioId, null, "LOGOUT", AuditoriaService.ip(request), null);
        }
        session.invalidate();   // dispara SesionesActivas.sessionDestroyed, que cierra los WebSocket de esta sesión
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private static void limpiar(HttpSession session) {
        session.removeAttribute("usuarioId"); session.removeAttribute("rol");
        SecurityContextHolder.clearContext();
    }
    private static String minutos(long segundos) {
        long m = (segundos + 59) / 60;
        return m <= 1 ? "1 minuto" : m + " minutos";
    }
    private void auditar(Integer id, String usuario, String accion, String ip, String detalle) {
        if (auditoria == null) return;
        try { auditoria.acceso(id, usuario, accion, ip, detalle); } catch (RuntimeException e) { /* la auditoría no debe impedir el acceso */ }
    }
}
