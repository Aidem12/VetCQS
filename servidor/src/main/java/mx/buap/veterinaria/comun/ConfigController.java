package mx.buap.veterinaria.comun;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final Negocio negocio;
    public ConfigController(Negocio negocio) { this.negocio = negocio; }
    public record NegocioDto(String nombre, String direccion, String telefono, String rfc, String leyenda) {}
    @GetMapping("/negocio")
    public NegocioDto negocio() {
        return new NegocioDto(negocio.getNombre(), negocio.getDireccion(), negocio.getTelefono(), negocio.getRfc(), negocio.getLeyenda());
    }
}
