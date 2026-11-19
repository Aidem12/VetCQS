package mx.buap.veterinaria.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record VentaRequest(
        @NotEmpty List<@Valid DetalleRequest> detalles,
        @NotNull @DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=8,fraction=2) BigDecimal descuento) {

    public record DetalleRequest(
            @NotNull Integer productoId,
            @NotNull @DecimalMin("0.01") @jakarta.validation.constraints.Digits(integer=8,fraction=2) BigDecimal cantidad) {
    }
}
