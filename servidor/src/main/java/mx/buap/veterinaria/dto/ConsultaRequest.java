package mx.buap.veterinaria.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsultaRequest(@NotNull Integer mascotaId, @NotNull Integer veterinarioId,
        @NotBlank String motivo, BigDecimal pesoKg, BigDecimal temperaturaC,
        String diagnostico, String tratamiento) {}
