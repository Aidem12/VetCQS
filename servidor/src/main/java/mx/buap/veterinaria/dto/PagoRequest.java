package mx.buap.veterinaria.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import mx.buap.veterinaria.model.Pago.Forma;

public record PagoRequest(
        @NotNull Forma forma,
        @NotNull @DecimalMin("0.01") @jakarta.validation.constraints.Digits(integer=8,fraction=2) BigDecimal monto,
        @DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=8,fraction=2) BigDecimal recibido) {
}
