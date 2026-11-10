package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
/** Ajuste de inventario (Iteración 3): cantidad positiva suma, negativa resta (merma, caducidad, conteo físico). */
public record AjusteRequest(@NotNull BigDecimal cantidad,
        @NotNull Integer usuarioId, @NotBlank @Size(max=200) String motivo) {}
