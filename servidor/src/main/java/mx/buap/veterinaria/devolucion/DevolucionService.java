package mx.buap.veterinaria.devolucion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.comun.Reloj;
import mx.buap.veterinaria.farmacia.Lote;
import mx.buap.veterinaria.farmacia.LoteRepository;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.service.AutorizacionService;
import mx.buap.veterinaria.service.TurnoService;

/**
 * Devoluciones y reembolsos controlados:
 * - solo sobre ventas PAGADAS, con autorización de administrador y motivo;
 * - nunca más de lo vendido por producto (considerando devoluciones previas);
 * - el importe se prorratea con el descuento de la venta;
 * - los productos que reingresan regresan al inventario (al lote del que salieron cuando manejan caducidad);
 * - el reembolso en efectivo sale del turno abierto y se descuenta del efectivo esperado del corte.
 */
@Service
public class DevolucionService {
    private final DevolucionRepository devoluciones;
    private final VentaRepository ventas;
    private final ProductoRepository productos;
    private final MovimientoInventarioRepository movimientos;
    private final LoteRepository lotes;
    private final UsuarioRepository usuarios;
    private final AutorizacionService autorizacion;
    private final TurnoService turnos;
    private final Reloj reloj;

    public DevolucionService(DevolucionRepository devoluciones, VentaRepository ventas, ProductoRepository productos,
            MovimientoInventarioRepository movimientos, LoteRepository lotes, UsuarioRepository usuarios,
            AutorizacionService autorizacion, TurnoService turnos, Reloj reloj) {
        this.devoluciones = devoluciones; this.ventas = ventas; this.productos = productos; this.movimientos = movimientos;
        this.lotes = lotes; this.usuarios = usuarios; this.autorizacion = autorizacion; this.turnos = turnos; this.reloj = reloj;
    }

    public record Renglon(Integer productoId, BigDecimal cantidad, Boolean reingresa) {}
    public record Solicitud(List<Renglon> renglones, String motivo, Devolucion.Forma formaReembolso,
            String usuarioAdmin, String claveAdmin) {}
    public record Devolvible(Integer productoId, String producto, String tipo, BigDecimal vendido, BigDecimal devuelto,
            BigDecimal disponible, BigDecimal precioNeto) {}

    private static ResponseStatusException error(HttpStatus s, String m) { return new ResponseStatusException(s, m); }

    @Transactional
    public Devolucion registrar(Integer ventaId, Solicitud s, Integer operadorId) {
        if (s == null || s.renglones() == null || s.renglones().isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Indique al menos un producto a devolver.");
        if (s.motivo() == null || s.motivo().isBlank() || s.motivo().trim().length() > 200)
            throw error(HttpStatus.BAD_REQUEST, "El motivo es obligatorio (máximo 200 caracteres).");
        if (s.formaReembolso() == null) throw error(HttpStatus.BAD_REQUEST, "Indique la forma de reembolso.");
        Venta venta = ventas.findByIdForUpdate(ventaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Venta no encontrada."));
        if (venta.getEstado() != Venta.Estado.PAGADA)
            throw error(HttpStatus.CONFLICT, "Solo se pueden devolver ventas pagadas.");
        Turno turno = turnos.propioAbierto(operadorId);
        Usuario admin = autorizacion.validar(s.usuarioAdmin(), s.claveAdmin());
        Usuario operador = usuarios.findById(operadorId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Sesión no válida."));

        BigDecimal factor = venta.getSubtotal().signum() == 0 ? BigDecimal.ONE
                : venta.getTotal().divide(venta.getSubtotal(), 10, RoundingMode.HALF_UP);
        Devolucion d = new Devolucion();
        d.setVentaId(venta.getId()); d.setTurnoId(turno.getId()); d.setUsuarioId(operadorId); d.setAutorizadoPor(admin.getId());
        d.setMotivo(s.motivo().trim()); d.setFormaReembolso(s.formaReembolso()); d.setFecha(reloj.ahora());
        BigDecimal monto = BigDecimal.ZERO;
        Set<Integer> vistos = new HashSet<>();
        List<Runnable> inventario = new ArrayList<>();
        for (Renglon r : s.renglones()) {
            if (r.productoId() == null || r.cantidad() == null || r.cantidad().signum() <= 0 || r.cantidad().scale() > 2)
                throw error(HttpStatus.BAD_REQUEST, "Cada renglón requiere producto y una cantidad positiva.");
            if (!vistos.add(r.productoId())) throw error(HttpStatus.BAD_REQUEST, "No repita productos en la devolución.");
            List<VentaDetalle> vendidos = venta.getDetalles().stream().filter(x -> x.getProducto().getId().equals(r.productoId())).toList();
            if (vendidos.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "El producto no forma parte de la venta.");
            BigDecimal vendido = vendidos.stream().map(VentaDetalle::getCantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal previo = devoluciones.devuelto(venta.getId(), r.productoId());
            if (previo.add(r.cantidad()).compareTo(vendido) > 0)
                throw error(HttpStatus.CONFLICT, "No se puede devolver más de lo vendido de " + vendidos.get(0).getProducto().getNombre()
                        + " (disponible: " + vendido.subtract(previo).stripTrailingZeros().toPlainString() + ").");
            BigDecimal precio = vendidos.get(0).getPrecioUnitario();
            BigDecimal importe = precio.multiply(r.cantidad()).multiply(factor).setScale(2, RoundingMode.HALF_UP);
            Producto p = vendidos.get(0).getProducto();
            boolean reingresa = "PRODUCTO".equals(p.getTipo()) && !Boolean.FALSE.equals(r.reingresa());
            d.agregar(new DevolucionDetalle(p, r.cantidad(), importe, reingresa));
            monto = monto.add(importe);
            if (reingresa) inventario.add(() -> reingresar(p.getId(), r.cantidad(), venta, operador, d));
        }
        // No se reembolsa más de lo cobrado (incluye devoluciones anteriores).
        if (devoluciones.reembolsado(venta.getId()).add(monto).compareTo(venta.getTotal()) > 0)
            throw error(HttpStatus.CONFLICT, "El reembolso supera el total pagado de la venta.");
        if (monto.signum() <= 0) throw error(HttpStatus.BAD_REQUEST, "El importe a reembolsar debe ser mayor a cero.");
        if (s.formaReembolso() == Devolucion.Forma.EFECTIVO && monto.compareTo(turnos.efectivoDisponible(turno)) > 0)
            throw error(HttpStatus.CONFLICT, "No hay efectivo suficiente en caja para el reembolso.");
        d.setMonto(monto);
        d.setFolio(String.format("D-%06d", devoluciones.siguienteFolio()));
        Devolucion guardada = devoluciones.save(d);
        inventario.forEach(Runnable::run);
        autorizacion.registrar(operadorId, admin.getId(), "DEVOLUCION", "devolucion", guardada.getId(),
                "Venta " + venta.getFolio() + " | " + guardada.getFolio() + " | " + s.formaReembolso() + " $" + monto + " | " + d.getMotivo());
        return guardada;
    }

    /** Regresa existencias al producto y, si maneja caducidad, a los lotes vigentes de los que salió esa venta. */
    private void reingresar(Integer productoId, BigDecimal cantidad, Venta venta, Usuario operador, Devolucion d) {
        Producto p = productos.findByIdForUpdate(productoId).orElseThrow();
        BigDecimal resto = cantidad;
        if (p.isManejaCaducidad()) {
            Map<Integer, BigDecimal> porLote = new LinkedHashMap<>();
            movimientos.findByProductoIdOrderByFechaDescIdDesc(productoId).stream()
                    .filter(m -> venta.getId().equals(m.getVentaId()) && m.getLoteId() != null && m.getTipo() == MovimientoInventario.Tipo.VENTA)
                    .forEach(m -> porLote.merge(m.getLoteId(), m.getCantidad().abs(), BigDecimal::add));
            for (var e : porLote.entrySet()) {
                if (resto.signum() <= 0) break;
                Lote l = lotes.findByIdForUpdate(e.getKey()).orElse(null);
                if (l == null || l.getCaducidad().isBefore(reloj.hoy())) continue;   // un lote vencido no se reabre
                BigDecimal parte = resto.min(e.getValue());
                l.setCantidad(l.getCantidad().add(parte));
                lotes.save(l);
                movimiento(p, operador, parte, venta, d, l.getId());
                resto = resto.subtract(parte);
            }
            if (resto.signum() > 0)
                throw error(HttpStatus.CONFLICT, "El lote de " + p.getNombre()
                        + " ya caducó; registre la devolución sin reingreso al inventario (merma).");
            p.setStockActual(p.getStockActual().add(cantidad));
        } else {
            p.setStockActual(p.getStockActual().add(cantidad));
            movimiento(p, operador, cantidad, venta, d, null);
        }
        productos.save(p);
    }

    private void movimiento(Producto p, Usuario operador, BigDecimal cantidad, Venta venta, Devolucion d, Integer loteId) {
        MovimientoInventario m = new MovimientoInventario();
        m.setProducto(p); m.setUsuario(operador); m.setTipo(MovimientoInventario.Tipo.DEVOLUCION); m.setCantidad(cantidad);
        m.setMotivo(d.getMotivo()); m.setReferencia(d.getFolio() + " / " + venta.getFolio());
        m.setVentaId(venta.getId()); m.setLoteId(loteId);
        movimientos.save(m);
    }

    @Transactional(readOnly = true)
    public List<Devolvible> devolvibles(Integer ventaId) {
        Venta venta = ventas.findById(ventaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Venta no encontrada."));
        BigDecimal factor = venta.getSubtotal().signum() == 0 ? BigDecimal.ONE
                : venta.getTotal().divide(venta.getSubtotal(), 10, RoundingMode.HALF_UP);
        Map<Integer, List<VentaDetalle>> porProducto = new LinkedHashMap<>();
        venta.getDetalles().forEach(x -> porProducto.computeIfAbsent(x.getProducto().getId(), k -> new ArrayList<>()).add(x));
        List<Devolvible> lista = new ArrayList<>();
        porProducto.forEach((id, renglones) -> {
            BigDecimal vendido = renglones.stream().map(VentaDetalle::getCantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal devuelto = devoluciones.devuelto(ventaId, id);
            Producto p = renglones.get(0).getProducto();
            lista.add(new Devolvible(id, p.getNombre(), p.getTipo(), vendido, devuelto, vendido.subtract(devuelto),
                    renglones.get(0).getPrecioUnitario().multiply(factor).setScale(2, RoundingMode.HALF_UP)));
        });
        return lista;
    }

    @Transactional(readOnly = true)
    public List<Devolucion> deVenta(Integer ventaId) { return devoluciones.findByVentaIdOrderByIdAsc(ventaId); }
}
