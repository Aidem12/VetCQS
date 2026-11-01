package mx.buap.veterinaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.servlet.http.HttpSession;
import mx.buap.veterinaria.controller.AuthController;
import mx.buap.veterinaria.dto.LoginRequest;
import mx.buap.veterinaria.model.Usuario;
import mx.buap.veterinaria.repository.UsuarioRepository;

class AuthControllerTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthController controller = new AuthController(usuarios, encoder);

    @Test
    void loginStoresRoleAndUserInHttpSession() {
        Usuario usuario = usuarioActivo("caja", "secreto123", Usuario.Rol.CAJERO);
        when(usuarios.findByUsuarioAndActivoTrue(eq("caja"))).thenReturn(Optional.of(usuario));

        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession();
        var response = controller.login(new LoginRequest("caja", "secreto123"), session, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(usuario.getId(), session.getAttribute("usuarioId"));
        assertEquals("CAJERO", session.getAttribute("rol"));
        assertNotNull(request.getSession(false));
    }

    @Test
    void invalidPasswordDoesNotCreateAuthenticatedSession() {
        Usuario usuario = usuarioActivo("caja", "secreto123", Usuario.Rol.CAJERO);
        when(usuarios.findByUsuarioAndActivoTrue(eq("caja"))).thenReturn(Optional.of(usuario));

        MockHttpServletRequest request = new MockHttpServletRequest();
        var response = controller.login(new LoginRequest("caja", "incorrecta"), request.getSession(),
                request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(null, request.getSession(false).getAttribute("usuarioId"));
        assertEquals(null, request.getSession(false).getAttribute("rol"));
    }

    private Usuario usuarioActivo(String nombreUsuario, String contrasena, Usuario.Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombreUsuario);
        usuario.setNombre("Usuario de prueba");
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setContrasenaHash(encoder.encode(contrasena));
        try {
            var id = Usuario.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(usuario, 42);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("No se pudo preparar el usuario de prueba", e);
        }
        return usuario;
    }
}
