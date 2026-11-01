package mx.buap.veterinaria.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // La sesión de escritorio usa /api/auth; los endpoints históricos se mantienen
        // compatibles hasta que ambos clientes adopten el token.
        http.csrf(csrf -> csrf.disable()).cors(cors -> {});
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/**", "/api/salud/**").permitAll().anyRequest().permitAll());
        return http.build();
    }
}
