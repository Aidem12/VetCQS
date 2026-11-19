package mx.buap.veterinaria;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import mx.buap.veterinaria.comun.*;
import mx.buap.veterinaria.controller.AuthController;
import mx.buap.veterinaria.dto.LoginRequest;
import mx.buap.veterinaria.model.Usuario;
import mx.buap.veterinaria.repository.UsuarioRepository;
import mx.buap.veterinaria.seguridad.*;

/** Bloqueo temporal por intentos fallidos de contraseña. */
class LimiteIntentosTest {
    final Reloj reloj = new Reloj();
    Instant ahora = LocalDate.of(2026, 11, 18).atTime(9, 0).atZone(Reloj.ZONA).toInstant();
    final LimiteIntentos limite = new LimiteIntentos(reloj);

    @BeforeEach void fijar() { mover(Duration.ZERO); }
    void mover(Duration d) { ahora = ahora.plus(d); reloj.fijar(Clock.fixed(ahora, Reloj.ZONA)); }

    @Test void cincoFallosBloqueanCincoMinutos() {
        String k = LimiteIntentos.clave("login", "Ana", null);
        for (int i = 0; i < 4; i++) assertFalse(limite.fallo(k));
        assertEquals(0, limite.segundosBloqueado(k));
        assertTrue(limite.fallo(k));
        assertTrue(limite.segundosBloqueado(LimiteIntentos.clave("login", " ana ", null)) > 0, "No distingue mayúsculas ni espacios");
        mover(Duration.ofMinutes(5));
        assertEquals(0, limite.segundosBloqueado(k));
    }

    @Test void fallosFueraDeLaVentanaNoCuentan() {
        String k = LimiteIntentos.clave("login", "ana", null);
        for (int i = 0; i < 4; i++) limite.fallo(k);
        mover(Duration.ofMinutes(16));
        assertFalse(limite.fallo(k));
        limite.exito(k);
        for (int i = 0; i < 4; i++) assertFalse(limite.fallo(k));
    }

    @Test void loginBloqueadoNoEvaluaLaContrasenaCorrecta() {
        var encoder = new BCryptPasswordEncoder();
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario ana = mock(Usuario.class);
        when(ana.getContrasenaHash()).thenReturn(encoder.encode("correcta1"));
        when(ana.getId()).thenReturn(3); when(ana.getUsuario()).thenReturn("ana"); when(ana.getNombre()).thenReturn("Ana");
        when(ana.getRol()).thenReturn(Usuario.Rol.VETERINARIO);
        when(usuarios.findByUsuarioAndActivoTrue(eq("ana"))).thenReturn(Optional.of(ana));
        AuditoriaService auditoria = mock(AuditoriaService.class);
        AuthController auth = new AuthController(usuarios, encoder, limite, null, auditoria);
        for (int i = 0; i < 4; i++) {
            var req = new MockHttpServletRequest();
            assertEquals(HttpStatus.UNAUTHORIZED, auth.login(new LoginRequest("ana", "mala" + i), req.getSession(), req).getStatusCode());
        }
        var req5 = new MockHttpServletRequest();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, auth.login(new LoginRequest("ana", "mala"), req5.getSession(), req5).getStatusCode());
        var req6 = new MockHttpServletRequest();
        var r = auth.login(new LoginRequest("ana", "correcta1"), req6.getSession(), req6);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, r.getStatusCode());
        assertNull(req6.getSession().getAttribute("usuarioId"));
        assertNotNull(r.getHeaders().getFirst("Retry-After"));
        verify(auditoria, atLeastOnce()).acceso(any(), eq("ana"), eq("LOGIN_FALLIDO"), any(), any());
        verify(auditoria, atLeastOnce()).acceso(any(), eq("ana"), eq("LOGIN_BLOQUEADO"), any(), any());
        mover(Duration.ofMinutes(5).plusSeconds(1));
        var req7 = new MockHttpServletRequest();
        assertEquals(HttpStatus.OK, auth.login(new LoginRequest("ana", "correcta1"), req7.getSession(), req7).getStatusCode());
        verify(auditoria).acceso(eq(3), eq("ana"), eq("LOGIN_OK"), any(), any());
    }
}
