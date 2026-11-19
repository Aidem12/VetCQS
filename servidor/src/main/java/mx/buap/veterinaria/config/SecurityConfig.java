package mx.buap.veterinaria.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpMethod;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
@Configuration
public class SecurityConfig {
    private final mx.buap.veterinaria.repository.UsuarioRepository usuarios;
    public SecurityConfig(mx.buap.veterinaria.repository.UsuarioRepository usuarios) { this.usuarios = usuarios; }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF con doble envío: el servidor entrega la cookie XSRF-TOKEN y los clientes la devuelven en la
        // cabecera X-XSRF-TOKEN en cada POST/PUT/PATCH/DELETE. El login queda exento porque aún no hay sesión.
        var csrfHandler = new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);   // genera la cookie desde la primera respuesta
        http.csrf(csrf -> csrf
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/api/auth/login"))
            .cors(cors -> {})
            .headers(h -> h
                .contentTypeOptions(c -> {})
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Inicie sesión."))
                .accessDeniedHandler((req, res, ex) -> res.sendError(HttpServletResponse.SC_FORBIDDEN,
                        ex instanceof org.springframework.security.web.csrf.CsrfException
                                ? "Token CSRF ausente o inválido. Vuelva a abrir la pantalla." : "No tiene permiso para esta operación.")));
        http.addFilterBefore(sessionAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        http.authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
            .requestMatchers("/api/auth/**", "/api/salud/**").permitAll()
            .requestMatchers("/api/empleados/**", "/api/inventario/**").hasRole("ADMINISTRADOR")
            .requestMatchers(HttpMethod.GET, "/api/productos/catalogo").hasRole("ADMINISTRADOR")
            .requestMatchers(HttpMethod.GET, "/api/turnos/historial", "/api/turnos/*/reporte").hasAnyRole("ADMINISTRADOR", "CAJERO")
            .requestMatchers(HttpMethod.GET, "/api/productos/**").hasAnyRole("ADMINISTRADOR", "CAJERO", "VETERINARIO")
            .requestMatchers("/api/productos/**").hasRole("ADMINISTRADOR")
            .requestMatchers(HttpMethod.POST, "/api/ventas/precuentas").hasAnyRole("ADMINISTRADOR", "VETERINARIO", "CAJERO")
            .requestMatchers("/api/ventas/**", "/api/turnos/**", "/ws/caja").hasAnyRole("ADMINISTRADOR", "CAJERO")
            .requestMatchers("/api/clinica/**").hasAnyRole("ADMINISTRADOR", "VETERINARIO")
            .requestMatchers("/api/agenda/**").hasAnyRole("ADMINISTRADOR", "VETERINARIO")
            .requestMatchers(HttpMethod.GET, "/api/farmacia/lotes/vigentes").hasAnyRole("ADMINISTRADOR", "CAJERO", "VETERINARIO")
            .requestMatchers("/api/farmacia/**", "/api/reportes/**", "/api/auditoria/**").hasRole("ADMINISTRADOR")
            .requestMatchers(HttpMethod.GET, "/api/config/negocio").authenticated()
            .requestMatchers("/api/facturas/**").hasAnyRole("ADMINISTRADOR", "CAJERO")
            .anyRequest().authenticated())
            .sessionManagement(session -> session
                // AuthController ya rota el id al iniciar sesión. Rotarlo en cada petición rompía las
                // peticiones simultáneas (Administración carga personal, productos y alertas a la vez).
                // Una sesión por usuario: la controla SesionesActivas al iniciar sesión.
                .sessionFixation(fixation -> fixation.none()));
        return http.build();
    }

    @Bean
    OncePerRequestFilter sessionAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                Object rol = request.getSession(false) == null ? null
                        : request.getSession(false).getAttribute("rol");
                Object id = request.getSession(false) == null ? null
                        : request.getSession(false).getAttribute("usuarioId");
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
                if (id instanceof Integer usuarioId && rol instanceof String) {
                    var usuario = usuarios.findById(usuarioId).filter(u -> u.isActivo() && u.getRol().name().equals(rol));
                    if (usuario.isEmpty()) {
                        request.getSession(false).invalidate();
                        chain.doFilter(request, response); return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(id.toString(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                }
                chain.doFilter(request, response);
            }
        };
    }
}
