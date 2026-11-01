package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record TurnoCierreRequest(@NotNull @DecimalMin("0.00") BigDecimal efectivoContado) {}
