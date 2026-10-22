package mx.buap.veterinaria.controller;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prueba de humo del entorno: confirma que el servidor responde y llega a PostgreSQL. */
@RestController
public class SaludController {

    private final JdbcTemplate jdbc;

    public SaludController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/salud")
    public Map<String, Object> salud() {
        Integer tablas = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                Integer.class);
        return Map.of("estado", "OK", "tablas", tablas, "hora", OffsetDateTime.now().toString());
    }
}
