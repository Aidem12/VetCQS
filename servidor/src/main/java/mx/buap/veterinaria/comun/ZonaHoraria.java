package mx.buap.veterinaria.comun;

import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * Fija la zona horaria del proceso a la del negocio (por defecto America/Mexico_City) para que las fechas
 * guardadas con LocalDateTime.now() coincidan con los cortes por día de los reportes, aunque el equipo o
 * el contenedor estén en UTC.
 */
@Configuration
public class ZonaHoraria {
    private final String zona;
    public ZonaHoraria(@Value("${app.zona-horaria:America/Mexico_City}") String zona) { this.zona = zona; }
    @PostConstruct void aplicar() { TimeZone.setDefault(TimeZone.getTimeZone(zona)); }
}
