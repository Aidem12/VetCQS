package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record MovimientoRequest(@NotNull @DecimalMin("0.01") BigDecimal cantidad,
        @NotNull Integer usuarioId, @NotBlank @Size(max=200) String motivo) {}
