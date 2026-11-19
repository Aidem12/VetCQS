package mx.buap.veterinaria.dto;

import jakarta.validation.constraints.*;

public record DuenoRequest(@NotBlank @Size(max = 120) String nombre,
        @Size(max = 20) @Pattern(regexp = "^$|[0-9+() -]{7,20}", message = "teléfono inválido") String telefono,
        @Size(max = 120) @Email String correo, @Size(max = 200) String direccion,
        @Size(max = 20) String telefonoAlterno, @Size(max = 500) String notas) {
    public DuenoRequest(String nombre, String telefono, String correo, String direccion) {
        this(nombre, telefono, correo, direccion, null, null);
    }
}
