package mx.buap.veterinaria.comun;

import java.time.*;
import org.springframework.stereotype.Component;

/** Hora oficial del negocio (America/Mexico_City); se puede sustituir en pruebas. */
@Component
public class Reloj {
    public static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private Clock clock = Clock.system(ZONA);
    public LocalDateTime ahora() { return LocalDateTime.now(clock); }
    public LocalDate hoy() { return LocalDate.now(clock); }
    public void fijar(Clock clock) { this.clock = clock; }
}
