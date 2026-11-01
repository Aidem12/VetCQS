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
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(cors -> {});
        http.addFilterBefore(sessionAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**", "/api/salud/**").permitAll()
            .requestMatchers("/api/empleados/**", "/api/inventario/**").hasRole("ADMINISTRADOR")
            .requestMatchers(HttpMethod.GET, "/api/productos/**").hasAnyRole("ADMINISTRADOR", "CAJERO")
            .requestMatchers("/api/productos/**").hasRole("ADMINISTRADOR")
            .requestMatchers("/api/ventas/**", "/api/turnos/**").hasRole("CAJERO")
            .requestMatchers("/api/clinica/**").hasRole("VETERINARIO")
            .requestMatchers("/api/facturas/**").hasAnyRole("ADMINISTRADOR", "CAJERO")
            .anyRequest().authenticated())
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId())
                .maximumSessions(1));
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
                if (rol != null && id != null) {
                    var auth = new UsernamePasswordAuthenticationToken(id.toString(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                }
                chain.doFilter(request, response);
            }
        };
    }
}
