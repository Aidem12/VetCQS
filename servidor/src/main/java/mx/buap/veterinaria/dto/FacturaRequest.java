package mx.buap.veterinaria.dto;
import jakarta.validation.constraints.*;
public record FacturaRequest(@NotBlank @Size(max=13) String rfcReceptor,@NotBlank @Size(max=250) String razonSocial,
 @NotBlank @Size(max=3) String regimenFiscal,@NotBlank @Pattern(regexp="\\d{5}") String codigoPostal,
 @NotBlank @Size(max=4) String usoCfdi,@NotBlank @Size(max=80) String idempotencia) {}
