package mx.buap.veterinaria.dto;
import jakarta.validation.constraints.*;
public record LoginRequest(@NotBlank @Size(max=40) String usuario, @NotBlank @Size(max=200) String contrasena) {}
