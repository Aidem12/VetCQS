package mx.buap.veterinaria.controller;
import java.util.Map;
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
import mx.buap.veterinaria.dto.LoginRequest;
import mx.buap.veterinaria.repository.UsuarioRepository;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UsuarioRepository usuarios; private final PasswordEncoder encoder;
    public AuthController(UsuarioRepository u, PasswordEncoder e) { usuarios=u; encoder=e; }
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest r, HttpSession session,
            HttpServletRequest request) {
        return usuarios.findByUsuarioAndActivoTrue(r.usuario())
            .filter(u -> encoder.matches(r.contrasena(), u.getContrasenaHash()))
            .<ResponseEntity<?>>map(u -> {
                request.changeSessionId();
                session.setAttribute("usuarioId", u.getId());
                session.setAttribute("rol", u.getRol().name());
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(u.getId().toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name()))));
                return ResponseEntity.ok(Map.of("id",u.getId(),"nombre",u.getNombre(),"usuario",u.getUsuario(),"rol",u.getRol()));
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Credenciales inválidas")));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
