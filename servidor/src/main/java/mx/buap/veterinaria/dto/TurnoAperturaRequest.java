package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record TurnoAperturaRequest(@NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal fondoInicial, Integer cajeroId, java.util.Map<String,Integer> denominaciones) {
 public TurnoAperturaRequest(BigDecimal fondoInicial,Integer cajeroId){this(fondoInicial,cajeroId,null);}
}
