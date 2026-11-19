package mx.buap.veterinaria.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record MascotaRequest(@NotBlank @Size(max = 60) String nombre, @NotBlank @Size(max = 40) String especie,
        @Size(max = 60) String raza, @Pattern(regexp = "[MH]", message = "use M (macho) o H (hembra)") String sexo,
        @PastOrPresent LocalDate fechaNacimiento,
        @DecimalMin(value = "0.01", message = "el peso debe ser mayor a 0 kg") @DecimalMax("999.99") @Digits(integer = 4, fraction = 2) BigDecimal pesoKg,
        @Size(max = 40) String identificacion, @Size(max = 40) String color, Boolean esterilizado,
        @Size(max = 4000) String observaciones) {
    public MascotaRequest(String nombre, String especie, String raza, String sexo, LocalDate fechaNacimiento, BigDecimal pesoKg) {
        this(nombre, especie, raza, sexo, fechaNacimiento, pesoKg, null, null, null, null);
    }
}
