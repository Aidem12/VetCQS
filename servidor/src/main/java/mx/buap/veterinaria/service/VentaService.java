package mx.buap.veterinaria.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import mx.buap.veterinaria.dto.PagoRequest;
import mx.buap.veterinaria.dto.VentaRequest;
import mx.buap.veterinaria.dto.PrecuentaRequest;
import mx.buap.veterinaria.model.Consulta;
import mx.buap.veterinaria.model.Pago;
import mx.buap.veterinaria.model.Producto;
import mx.buap.veterinaria.model.Venta;
import mx.buap.veterinaria.model.VentaDetalle;
import mx.buap.veterinaria.repository.ProductoRepository;
import mx.buap.veterinaria.repository.PagoRepository;
import mx.buap.veterinaria.repository.VentaRepository;
import mx.buap.veterinaria.repository.ConsultaRepository;

@Service
public class VentaService {

    private final VentaRepository ventas;
    private final ProductoRepository productos;
    private final PagoRepository pagos;
    private final ConsultaRepository consultas;

    public VentaService(VentaRepository ventas, ProductoRepository productos, PagoRepository pagos,
            ConsultaRepository consultas) {
        this.ventas = ventas;
        this.productos = productos;
        this.pagos = pagos;
        this.consultas = consultas;
    }

    @Transactional
    public Venta crearPrecuenta(PrecuentaRequest request) {
        Consulta consulta = consultas.findById(request.consultaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Consulta no encontrada"));
        VentaRequest ventaRequest = new VentaRequest(
                request.detalles().stream()
                        .map(d -> new VentaRequest.DetalleRequest(d.productoId(), d.cantidad()))
                        .toList(), BigDecimal.ZERO);
        Venta venta = crear(ventaRequest);
        venta.setConsulta(consulta);
        venta.setEstado(Venta.Estado.PRECUENTA);
        return ventas.save(venta);
    }

    @Transactional
    public Venta crear(VentaRequest request) {
        Venta venta = new Venta();
        BigDecimal subtotal = BigDecimal.ZERO;
        Map<Integer, BigDecimal> cantidades = new HashMap<>();
        for (VentaRequest.DetalleRequest requestDetalle : request.detalles()) {
            Producto producto = productos.findById(requestDetalle.productoId())
                    .filter(Producto::isActivo)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Producto no encontrado o inactivo"));
            BigDecimal cantidadSolicitada = cantidades.merge(
                    producto.getId(), requestDetalle.cantidad(), BigDecimal::add);
            if (producto.getTipo().equals("PRODUCTO")
                    && producto.getStockActual().compareTo(cantidadSolicitada) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Existencia insuficiente para: " + producto.getNombre());
            }
            VentaDetalle detalle = new VentaDetalle();
            detalle.setProducto(producto);
            detalle.setCantidad(requestDetalle.cantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            venta.agregarDetalle(detalle);
            subtotal = subtotal.add(producto.getPrecio().multiply(requestDetalle.cantidad()));
        }
        BigDecimal descuento = request.descuento().setScale(2, RoundingMode.HALF_UP);
        if (descuento.compareTo(subtotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El descuento no puede superar el subtotal");
        }
        venta.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        venta.setDescuento(descuento);
        venta.setTotal(subtotal.subtract(descuento).setScale(2, RoundingMode.HALF_UP));
        venta.setFolio("V-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return ventas.save(venta);
    }

    @Transactional
    public Venta cobrar(Integer id, List<PagoRequest> requests) {
        Venta venta = ventas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
        if (venta.getEstado() != Venta.Estado.ABIERTA
                && venta.getEstado() != Venta.Estado.PRECUENTA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta ya no está disponible para cobro");
        }
        BigDecimal pagado = BigDecimal.ZERO;
        for (PagoRequest request : requests) {
            if (request.forma() == Pago.Forma.EFECTIVO
                    && (request.recibido() == null || request.recibido().compareTo(request.monto()) < 0)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El efectivo recibido debe cubrir el monto");
            }
            Pago pago = new Pago();
            pago.setVenta(venta);
            pago.setForma(request.forma());
            pago.setMonto(request.monto());
            pago.setRecibido(request.recibido());
            pagos.save(pago);
            pagado = pagado.add(request.monto());
        }
        if (pagado.compareTo(venta.getTotal()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La suma de pagos debe ser igual al total");
        }
        for (VentaDetalle detalle : venta.getDetalles()) {
            Producto producto = detalle.getProducto();
            if (producto.getTipo().equals("PRODUCTO")) {
                producto.setStockActual(producto.getStockActual().subtract(detalle.getCantidad()));
                productos.save(producto);
            }
        }
        venta.setEstado(Venta.Estado.PAGADA);
        return ventas.save(venta);
    }
}
