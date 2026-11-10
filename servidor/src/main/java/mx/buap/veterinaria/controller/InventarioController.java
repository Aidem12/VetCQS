package mx.buap.veterinaria.controller;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.dto.AjusteRequest;
import mx.buap.veterinaria.dto.AlertaStock;
import mx.buap.veterinaria.dto.MovimientoKardex;
import mx.buap.veterinaria.dto.MovimientoRequest;
import mx.buap.veterinaria.model.MovimientoInventario;
import mx.buap.veterinaria.service.InventarioService;
@RestController @RequestMapping("/api/inventario")
public class InventarioController {
    private final InventarioService service;
    public InventarioController(InventarioService s) { service=s; }
    @PostMapping("/{productoId}/entradas") @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventario entrada(@PathVariable Integer productoId, @Valid @RequestBody MovimientoRequest r,
            HttpSession session) {
        validarOperador(session, r.usuarioId());
        return service.entrada(productoId,r);
    }
    /** Iteración 3: merma, caducidad o corrección por conteo físico (cantidad con signo). */
    @PostMapping("/{productoId}/ajustes") @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventario ajuste(@PathVariable Integer productoId, @Valid @RequestBody AjusteRequest r,
            HttpSession session) {
        validarOperador(session, r.usuarioId());
        return service.ajuste(productoId, r);
    }
    /** RF-14: productos en o por debajo de su stock mínimo. */
    @GetMapping("/alertas")
    public List<AlertaStock> alertas() { return service.alertas(); }
    @GetMapping("/{productoId}/movimientos")
    public List<MovimientoInventario> historial(@PathVariable Integer productoId) { return service.historial(productoId); }
    /** Kardex: movimientos con la existencia que quedó después de cada uno. */
    @GetMapping("/{productoId}/kardex")
    public List<MovimientoKardex> kardex(@PathVariable Integer productoId) { return service.kardex(productoId); }

    private static void validarOperador(HttpSession session, Integer usuarioId) {
        Object operador = session.getAttribute("usuarioId");
        if (operador == null || !operador.toString().equals(String.valueOf(usuarioId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El operador debe corresponder a la sesión");
        }
    }
}
