package mx.buap.veterinaria.dto;
import jakarta.validation.constraints.*;
public record FacturaRequest(@NotBlank @Pattern(regexp="[A-Za-zÑñ&]{3,4}[0-9]{6}[A-Za-z0-9]{3}") String rfcReceptor,@NotBlank @Size(max=250) String razonSocial,
 @NotBlank @Pattern(regexp="[0-9]{3}") String regimenFiscal,@NotBlank @Pattern(regexp="\\d{5}") String codigoPostal,
 @NotBlank @Pattern(regexp="[A-Za-z][0-9]{2}") String usoCfdi,@NotBlank @Size(max=80) String idempotencia) {}
