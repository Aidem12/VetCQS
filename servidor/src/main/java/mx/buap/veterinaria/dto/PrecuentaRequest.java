package mx.buap.veterinaria.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record PrecuentaRequest(@NotNull Integer consultaId, @NotEmpty List<@Valid Detalle> detalles) {
    public record Detalle(@NotNull Integer productoId, @NotNull BigDecimal cantidad) {}
}
