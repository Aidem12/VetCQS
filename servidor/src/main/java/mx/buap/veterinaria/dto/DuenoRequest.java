package mx.buap.veterinaria.dto;

import jakarta.validation.constraints.NotBlank;

public record DuenoRequest(@NotBlank String nombre, String telefono, String correo, String direccion) {}
