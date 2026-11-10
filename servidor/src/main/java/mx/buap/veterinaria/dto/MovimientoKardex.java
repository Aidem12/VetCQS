package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** Renglón del kardex: un movimiento y la existencia que quedó después de aplicarlo. */
public record MovimientoKardex(Integer id, LocalDateTime fecha, String tipo, BigDecimal cantidad,
        BigDecimal saldo, String motivo, String referencia, String usuario) {}
