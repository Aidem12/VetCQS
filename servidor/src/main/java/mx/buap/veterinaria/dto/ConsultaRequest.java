package mx.buap.veterinaria.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

/** Signos vitales con unidades: peso en kg, temperatura en °C, frecuencias por minuto, condición corporal 1-9. */
public record ConsultaRequest(@NotNull Integer mascotaId, Integer veterinarioId,
        @NotBlank @Size(max = 250) String motivo,
        @DecimalMin(value = "0.01", message = "el peso debe ser mayor a 0 kg") @DecimalMax("999.99") @Digits(integer = 4, fraction = 2) BigDecimal pesoKg,
        @DecimalMin(value = "30.0", message = "temperatura fuera de rango (30-45 °C)") @DecimalMax(value = "45.0", message = "temperatura fuera de rango (30-45 °C)") @Digits(integer = 2, fraction = 1) BigDecimal temperaturaC,
        @Size(max = 8000) String diagnostico, @Size(max = 8000) String tratamiento,
        @Min(10) @Max(400) Integer frecuenciaCardiaca, @Min(4) @Max(200) Integer frecuenciaRespiratoria,
        @Min(1) @Max(9) Integer condicionCorporal, @Size(max = 8000) String evolucion, Integer citaId) {
    public ConsultaRequest(Integer mascotaId, Integer veterinarioId, String motivo, BigDecimal pesoKg,
            BigDecimal temperaturaC, String diagnostico, String tratamiento) {
        this(mascotaId, veterinarioId, motivo, pesoKg, temperaturaC, diagnostico, tratamiento, null, null, null, null, null);
    }
}
