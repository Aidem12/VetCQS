package mx.buap.veterinaria.controller;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import mx.buap.veterinaria.dto.LoginRequest;
import mx.buap.veterinaria.repository.UsuarioRepository;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UsuarioRepository usuarios; private final PasswordEncoder encoder;
    public AuthController(UsuarioRepository u, PasswordEncoder e) { usuarios=u; encoder=e; }
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest r, HttpSession session) {
        return usuarios.findByUsuarioAndActivoTrue(r.usuario())
            .filter(u -> encoder.matches(r.contrasena(), u.getContrasenaHash()))
            .<ResponseEntity<?>>map(u -> {
                session.setAttribute("usuarioId", u.getId());
                session.setAttribute("rol", u.getRol().name());
                return ResponseEntity.ok(Map.of("id",u.getId(),"nombre",u.getNombre(),"usuario",u.getUsuario(),"rol",u.getRol()));
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Credenciales inválidas")));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }
}
