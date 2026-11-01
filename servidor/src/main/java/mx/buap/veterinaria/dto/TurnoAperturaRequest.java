package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record TurnoAperturaRequest(@NotNull @DecimalMin("0.00") BigDecimal fondoInicial, @NotNull Integer cajeroId) {}
