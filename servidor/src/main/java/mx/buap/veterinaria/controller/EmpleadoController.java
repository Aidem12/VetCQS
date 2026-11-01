package mx.buap.veterinaria.controller;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import mx.buap.veterinaria.dto.EmpleadoRequest;
import mx.buap.veterinaria.model.Usuario;
import mx.buap.veterinaria.repository.UsuarioRepository;
@RestController @RequestMapping("/api/empleados")
public class EmpleadoController {
    private final UsuarioRepository usuarios; private final PasswordEncoder encoder;
    public EmpleadoController(UsuarioRepository u, PasswordEncoder e) { usuarios=u; encoder=e; }
    @GetMapping public List<Usuario> listar() { return usuarios.findAll(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Usuario crear(@Valid @RequestBody EmpleadoRequest r) {
        if (r.contrasena()==null || r.contrasena().length()<8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La contraseña debe tener 8 caracteres");
        Usuario u=new Usuario(); u.setNombre(r.nombre()); u.setUsuario(r.usuario()); u.setRol(r.rol()); u.setActivo(true); u.setContrasenaHash(encoder.encode(r.contrasena())); return usuarios.save(u);
    }
    @PutMapping("/{id}/desactivar")
    public Usuario desactivar(@PathVariable Integer id) { Usuario u=usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); u.setActivo(false); return usuarios.save(u); }
}
