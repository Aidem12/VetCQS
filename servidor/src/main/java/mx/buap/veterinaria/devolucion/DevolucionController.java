package mx.buap.veterinaria.devolucion;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

/** Devoluciones de una venta (Caja o Administrador; protegido por la regla /api/ventas/**). */
@RestController
@RequestMapping("/api/ventas/{ventaId}/devoluciones")
public class DevolucionController {
    private final DevolucionService servicio;
    public DevolucionController(DevolucionService servicio) { this.servicio = servicio; }

    public record DetalleVista(Integer productoId, String producto, BigDecimal cantidad, BigDecimal importe, boolean reingresa) {}
    public record Vista(Integer id, String folio, Integer ventaId, String fecha, String motivo, String formaReembolso,
            BigDecimal monto, Integer autorizadoPor, List<DetalleVista> detalles) {
        static Vista de(Devolucion d) {
            return new Vista(d.getId(), d.getFolio(), d.getVentaId(), d.getFecha().toString(), d.getMotivo(), d.getFormaReembolso().name(),
                    d.getMonto(), d.getAutorizadoPor(), d.getDetalles().stream().map(x -> new DetalleVista(x.getProducto().getId(),
                            x.getProducto().getNombre(), x.getCantidad(), x.getImporte(), x.isReingresa())).toList());
        }
    }

    @GetMapping("/disponibles")
    public List<DevolucionService.Devolvible> disponibles(@PathVariable Integer ventaId) { return servicio.devolvibles(ventaId); }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Vista> listar(@PathVariable Integer ventaId) { return servicio.deVenta(ventaId).stream().map(Vista::de).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Vista registrar(@PathVariable Integer ventaId, @RequestBody DevolucionService.Solicitud solicitud, HttpSession session) {
        return Vista.de(servicio.registrar(ventaId, solicitud, (Integer) session.getAttribute("usuarioId")));
    }
}
