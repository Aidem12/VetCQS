package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record AutorizacionRequest(@NotBlank String usuarioAdmin,@NotBlank String claveAdmin,
 @NotBlank @Size(max=200) String motivo,@NotBlank String accion,Integer detalleId,@DecimalMin("0.00") BigDecimal importe) {}
