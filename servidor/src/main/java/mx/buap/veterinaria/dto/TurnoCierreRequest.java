package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import java.util.Map;
import jakarta.validation.constraints.*;
public record TurnoCierreRequest(@NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal efectivoContado,
 Map<String,@Min(0) @Max(1000000) Integer> denominaciones,
 @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal tarjetaContada,
 @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal transferenciaContada) {}
