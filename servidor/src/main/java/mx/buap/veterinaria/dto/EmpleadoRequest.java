package mx.buap.veterinaria.dto;
import jakarta.validation.constraints.*;
import mx.buap.veterinaria.model.Usuario.Rol;
public record EmpleadoRequest(@NotBlank @Size(max=100) String nombre, @NotBlank @Size(max=40) String usuario,
        @Size(min=8,max=200) String contrasena, @NotNull Rol rol) {}
