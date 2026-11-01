package mx.buap.veterinaria.controller;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import mx.buap.veterinaria.dto.MovimientoRequest;
import mx.buap.veterinaria.model.MovimientoInventario;
import mx.buap.veterinaria.service.InventarioService;
@RestController @RequestMapping("/api/inventario")
public class InventarioController {
    private final InventarioService service;
    public InventarioController(InventarioService s) { service=s; }
    @PostMapping("/{productoId}/entradas") @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventario entrada(@PathVariable Integer productoId, @Valid @RequestBody MovimientoRequest r) { return service.entrada(productoId,r); }
    @GetMapping("/{productoId}/movimientos")
    public List<MovimientoInventario> historial(@PathVariable Integer productoId) { return service.historial(productoId); }
}
