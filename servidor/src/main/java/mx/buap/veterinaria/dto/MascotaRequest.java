package mx.buap.veterinaria.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;

public record MascotaRequest(@NotBlank String nombre, @NotBlank String especie, String raza,
        String sexo, LocalDate fechaNacimiento, BigDecimal pesoKg) {}
