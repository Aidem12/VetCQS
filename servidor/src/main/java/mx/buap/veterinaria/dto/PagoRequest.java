package mx.buap.veterinaria.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import mx.buap.veterinaria.model.Pago.Forma;

public record PagoRequest(
        @NotNull Forma forma,
        @NotNull @DecimalMin("0.01") BigDecimal monto,
        @DecimalMin("0.00") BigDecimal recibido) {
}
