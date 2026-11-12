package mx.buap.veterinaria;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import mx.buap.veterinaria.controller.EmpleadoController;
import mx.buap.veterinaria.dto.ContrasenaRequest;
import mx.buap.veterinaria.model.Usuario;
import mx.buap.veterinaria.repository.UsuarioRepository;

/** Reglas para eliminar, reactivar y cambiar contraseña del personal. */
class EmpleadoAdministracionTest {
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final EmpleadoController controller = new EmpleadoController(usuarios, encoder);
    final HttpSession session = mock(HttpSession.class);

    Usuario usuario(int id, Usuario.Rol rol, boolean activo, String hash) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(id); when(u.getRol()).thenReturn(rol);
        when(u.isActivo()).thenReturn(activo); when(u.getContrasenaHash()).thenReturn(hash);
        when(u.getNombre()).thenReturn("Persona " + id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test void noSePuedeEliminarElPropioUsuario() {
        usuario(3, Usuario.Rol.CAJERO, true, "x");
        when(session.getAttribute("usuarioId")).thenReturn(3);
        assertThrows(ResponseStatusException.class, () -> controller.eliminar(3, session));
        verify(usuarios, never()).delete(any());
    }

    @Test void debeQuedarUnAdministradorActivo() {
        usuario(1, Usuario.Rol.ADMINISTRADOR, true, "x");
        when(session.getAttribute("usuarioId")).thenReturn(9);
        when(usuarios.countByRolAndActivoTrue(Usuario.Rol.ADMINISTRADOR)).thenReturn(1L);
        assertThrows(ResponseStatusException.class, () -> controller.desactivar(1, session));
        assertThrows(ResponseStatusException.class, () -> controller.eliminar(1, session));
    }

    @Test void conHistorialNoSeBorraYPideDesactivar() {
        Usuario u = usuario(4, Usuario.Rol.CAJERO, true, "x");
        when(session.getAttribute("usuarioId")).thenReturn(1);
        doThrow(new DataIntegrityViolationException("fk")).when(usuarios).delete(u);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> controller.eliminar(4, session));
        assertTrue(e.getReason().contains("Desactivar"));
    }

    @Test void sinContrasenaNoSeReactivaYConContrasenaSi() {
        Usuario u = usuario(2, Usuario.Rol.VETERINARIO, false, null);
        assertThrows(ResponseStatusException.class, () -> controller.activar(2));
        when(encoder.encode("nueva12345")).thenReturn("hash");
        controller.cambiarContrasena(2, new ContrasenaRequest("nueva12345"));
        verify(u).setContrasenaHash("hash");
    }
}
