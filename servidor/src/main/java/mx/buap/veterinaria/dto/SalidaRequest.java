package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record SalidaRequest(@NotBlank @Size(max=200) String concepto,@NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal monto,
 @NotBlank String usuarioAdmin,@NotBlank String claveAdmin) {}
