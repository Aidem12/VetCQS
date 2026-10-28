package mx.buap.veterinaria.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ProductoRequest(
        String codigoBarras,
        @NotBlank String nombre,
        @NotBlank @Pattern(regexp = "PRODUCTO|SERVICIO") String tipo,
        @NotNull @DecimalMin("0.00") BigDecimal precio,
        @NotNull @DecimalMin("0.00") BigDecimal stockActual,
        @NotNull @DecimalMin("0.00") BigDecimal stockMinimo,
        boolean manejaCaducidad) {
}
