package mx.buap.veterinaria.comun;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import mx.buap.veterinaria.model.Usuario;
import mx.buap.veterinaria.repository.UsuarioRepository;

/** Obtiene el usuario autenticado de la sesión; la identidad nunca se toma del cuerpo de la petición. */
@Component
public class SesionActual {
    private final UsuarioRepository usuarios;
    public SesionActual(UsuarioRepository usuarios) { this.usuarios = usuarios; }

    public Integer id(HttpSession session) {
        Object id = session == null ? null : session.getAttribute("usuarioId");
        if (!(id instanceof Integer valor)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicie sesión.");
        return valor;
    }
    public Usuario usuario(HttpSession session) {
        return usuarios.findById(id(session)).filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida."));
    }
    public static boolean esAdministrador(Usuario u) { return u.getRol() == Usuario.Rol.ADMINISTRADOR; }
}
