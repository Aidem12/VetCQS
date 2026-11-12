package mx.buap.veterinaria.controller;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import mx.buap.veterinaria.dto.ContrasenaRequest;
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
        if (usuarios.findByUsuario(r.usuario().trim()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"El nombre de usuario ya existe. Elija otro.");
        Usuario u=new Usuario(); u.setNombre(r.nombre()); u.setUsuario(r.usuario().trim()); u.setRol(r.rol()); u.setActivo(true); u.setContrasenaHash(encoder.encode(r.contrasena())); return usuarios.save(u);
    }
    @PutMapping("/{id}/desactivar")
    public Usuario desactivar(@PathVariable Integer id, HttpSession session) {
        Usuario u = buscar(id);
        protegerCuentaPropiaYUltimoAdmin(u, session, "desactivar");
        u.setActivo(false); return usuarios.save(u);
    }
    @PutMapping("/{id}/activar")
    public Usuario activar(@PathVariable Integer id) {
        Usuario u = buscar(id);
        if (u.getContrasenaHash() == null || u.getContrasenaHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este usuario no tiene contraseña. Asígnele una con Cambiar contraseña antes de activarlo.");
        }
        u.setActivo(true); return usuarios.save(u);
    }
    @PutMapping("/{id}/contrasena")
    public Usuario cambiarContrasena(@PathVariable Integer id, @Valid @RequestBody ContrasenaRequest r) {
        Usuario u = buscar(id);
        u.setContrasenaHash(encoder.encode(r.contrasena()));
        return usuarios.save(u);
    }
    /** Borra el usuario solo si no tiene historial; si lo tiene, hay que desactivarlo para no perder ventas ni consultas. */
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id, HttpSession session) {
        Usuario u = buscar(id);
        protegerCuentaPropiaYUltimoAdmin(u, session, "eliminar");
        try {
            usuarios.delete(u);
            usuarios.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, u.getNombre()
                    + " ya tiene ventas, consultas, turnos o movimientos registrados. Para no perder ese historial "
                    + "no se puede borrar; use Desactivar.");
        }
    }

    private Usuario buscar(Integer id) {
        return usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
    private void protegerCuentaPropiaYUltimoAdmin(Usuario u, HttpSession session, String accion) {
        Object actual = session.getAttribute("usuarioId");
        if (actual != null && actual.toString().equals(String.valueOf(u.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puede " + accion + " su propio usuario.");
        }
        if (u.getRol() == Usuario.Rol.ADMINISTRADOR && u.isActivo()
                && usuarios.countByRolAndActivoTrue(Usuario.Rol.ADMINISTRADOR) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debe quedar al menos un administrador activo.");
        }
    }
}
