package mx.buap.veterinaria.dto;
import jakarta.validation.constraints.*;
/** Nueva contraseña asignada por el administrador. */
public record ContrasenaRequest(@NotBlank @Size(min=8, max=200, message="La contraseña debe tener al menos 8 caracteres") String contrasena) {}
