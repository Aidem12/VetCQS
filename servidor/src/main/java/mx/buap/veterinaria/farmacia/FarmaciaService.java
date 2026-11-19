package mx.buap.veterinaria.farmacia;

import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.comun.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

/**
 * Proveedores, compras con lote, caducidades y salidas FEFO.
 * Para productos que manejan caducidad, la existencia general es siempre la suma de sus lotes.
 */
@Service
public class FarmaciaService {
    private final ProveedorRepository proveedores;
    private final CompraRepository compras;
    private final LoteRepository lotes;
    private final ProductoRepository productos;
    private final MovimientoInventarioRepository movimientos;
    private final AuditoriaService auditoria;
    private final Reloj reloj;

    public FarmaciaService(ProveedorRepository proveedores, CompraRepository compras, LoteRepository lotes,
            ProductoRepository productos, MovimientoInventarioRepository movimientos, AuditoriaService auditoria, Reloj reloj) {
        this.proveedores = proveedores; this.compras = compras; this.lotes = lotes; this.productos = productos;
        this.movimientos = movimientos; this.auditoria = auditoria; this.reloj = reloj;
    }

    public record ProveedorDatos(String nombre, String rfc, String contacto, String telefono, String correo,
            String direccion, String notas) {}
    public record RenglonCompra(Integer productoId, String numeroLote, LocalDate caducidad, BigDecimal cantidad,
            BigDecimal costoUnitario) {}
    /** Lote usado en una salida, para registrar trazabilidad. */
    public record Consumo(Integer loteId, String numeroLote, BigDecimal cantidad) {}

    // ------------------------------------------------------------------ proveedores
    @Transactional
    public Proveedor guardarProveedor(Integer id, ProveedorDatos d, Usuario autor) {
        Proveedor p = id == null ? new Proveedor()
                : proveedores.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Proveedor no encontrado."));
        String nombre = requerido(d.nombre(), "el nombre del proveedor", 150);
        proveedores.findByNombreIgnoreCase(nombre).filter(o -> !o.getId().equals(p.getId())).ifPresent(o -> {
            throw error(HttpStatus.CONFLICT, "Ya existe un proveedor con ese nombre.");
        });
        if (d.rfc() != null && !d.rfc().isBlank() && !d.rfc().trim().toUpperCase().matches("[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}"))
            throw error(HttpStatus.BAD_REQUEST, "El RFC del proveedor no tiene un formato válido.");
        p.setNombre(nombre); p.setRfc(vacio(d.rfc()) ? null : d.rfc().trim().toUpperCase());
        p.setContacto(opcional(d.contacto(), 120)); p.setTelefono(opcional(d.telefono(), 20));
        p.setCorreo(opcional(d.correo(), 120)); p.setDireccion(opcional(d.direccion(), 200)); p.setNotas(opcional(d.notas(), 500));
        Proveedor guardado = proveedores.save(p);
        auditoria.registrar(autor.getId(), id == null ? "PROVEEDOR_ALTA" : "PROVEEDOR_EDICION", "proveedor", guardado.getId(), nombre);
        return guardado;
    }

    @Transactional
    public Proveedor activarProveedor(Integer id, boolean activo, Usuario autor) {
        Proveedor p = proveedores.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Proveedor no encontrado."));
        p.setActivo(activo);
        auditoria.registrar(autor.getId(), activo ? "PROVEEDOR_ACTIVAR" : "PROVEEDOR_DESACTIVAR", "proveedor", id, p.getNombre());
        return p;
    }

    // ------------------------------------------------------------------ compras y lotes
    @Transactional
    public Compra registrarCompra(Integer proveedorId, String documento, String notas, List<RenglonCompra> renglones, Usuario autor) {
        if (renglones == null || renglones.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Agregue al menos un producto a la compra.");
        Proveedor proveedor = proveedores.findById(proveedorId).filter(Proveedor::isActivo)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "El proveedor no existe o está inactivo."));
        Compra compra = new Compra();
        compra.setProveedor(proveedor); compra.setDocumento(opcional(documento, 60)); compra.setNotas(opcional(notas, 300));
        compra.setUsuario(autor);
        compras.save(compra);
        BigDecimal total = BigDecimal.ZERO;
        LocalDate hoy = reloj.hoy();
        for (RenglonCompra r : renglones) {
            BigDecimal cantidad = cantidadValida(r.cantidad());
            BigDecimal costo = dinero(r.costoUnitario(), "el costo unitario");
            Producto producto = productos.findByIdForUpdate(r.productoId()).filter(Producto::isActivo)
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Producto no encontrado o inactivo."));
            if (!"PRODUCTO".equals(producto.getTipo())) throw error(HttpStatus.BAD_REQUEST, "Los servicios no se compran ni manejan existencias.");
            CompraDetalle detalle = new CompraDetalle();
            detalle.setProducto(producto); detalle.setCantidad(cantidad); detalle.setCostoUnitario(costo);
            Lote lote = null;
            if (producto.isManejaCaducidad()) {
                String numero = requerido(r.numeroLote(), "el número de lote de " + producto.getNombre(), 40);
                if (r.caducidad() == null) throw error(HttpStatus.BAD_REQUEST, "Indique la caducidad de " + producto.getNombre() + ".");
                if (!r.caducidad().isAfter(hoy)) throw error(HttpStatus.BAD_REQUEST, "No se puede recibir " + producto.getNombre() + ": el lote ya está caducado.");
                lote = lotes.findByProductoIdAndNumeroLoteIgnoreCase(producto.getId(), numero).orElse(null);
                if (lote != null && !lote.getCaducidad().equals(r.caducidad()))
                    throw error(HttpStatus.CONFLICT, "El lote " + numero + " ya existe con otra caducidad.");
                if (lote == null) {
                    lote = new Lote();
                    lote.setProducto(producto); lote.setNumeroLote(numero); lote.setCaducidad(r.caducidad());
                    lote.setCantidad(BigDecimal.ZERO); lote.setCantidadRecibida(BigDecimal.ZERO);
                }
                lote.setCantidad(lote.getCantidad().add(cantidad));
                lote.setCantidadRecibida(Objects.requireNonNullElse(lote.getCantidadRecibida(), BigDecimal.ZERO).add(cantidad));
                lote.setCostoUnitario(costo); lote.setProveedor(proveedor); lote.setCompraId(compra.getId());
                lote = lotes.save(lote);
                detalle.setLote(lote);
            }
            producto.setStockActual(producto.getStockActual().add(cantidad));
            producto.setCostoUltimo(costo);
            productos.save(producto);
            compra.agregar(detalle);
            movimientos.save(movimiento(producto, autor, MovimientoInventario.Tipo.ENTRADA, cantidad,
                    "Compra a " + proveedor.getNombre(), "COMPRA-" + compra.getId(), lote == null ? null : lote.getId(), null));
            total = total.add(costo.multiply(cantidad));
        }
        compra.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        Compra guardada = compras.save(compra);
        auditoria.registrar(autor.getId(), "COMPRA_ALTA", "compra", guardada.getId(),
                proveedor.getNombre() + " · " + renglones.size() + " renglones · $" + guardada.getTotal());
        return guardada;
    }

    /** Existencia que sí se puede vender: la de lotes vigentes, o la general si el producto no maneja caducidad. */
    @Transactional(readOnly = true)
    public BigDecimal disponibleParaVenta(Producto producto) {
        if (!"PRODUCTO".equals(producto.getTipo())) return null;
        if (!producto.isManejaCaducidad()) return producto.getStockActual();
        return lotes.disponibleVigente(producto.getId(), reloj.hoy());
    }

    /**
     * Descuenta una salida por FEFO usando solo lotes vigentes. El producto debe venir bloqueado.
     * Registra un movimiento por lote y actualiza la existencia general una sola vez.
     */
    @Transactional
    public List<Consumo> consumirFefo(Producto producto, BigDecimal cantidad, Usuario operador,
            MovimientoInventario.Tipo tipo, String motivo, String referencia, Integer ventaId) {
        LocalDate hoy = reloj.hoy();
        BigDecimal pendiente = cantidad;
        List<Consumo> usados = new ArrayList<>();
        boolean habiaCaducados = false;
        for (Lote lote : lotes.paraConsumir(producto.getId())) {
            if (lote.vencido(hoy)) { habiaCaducados = true; continue; }
            if (pendiente.signum() <= 0) break;
            BigDecimal tomar = lote.getCantidad().min(pendiente);
            lote.setCantidad(lote.getCantidad().subtract(tomar));
            pendiente = pendiente.subtract(tomar);
            usados.add(new Consumo(lote.getId(), lote.getNumeroLote(), tomar));
        }
        if (pendiente.signum() > 0) {
            throw error(HttpStatus.CONFLICT, habiaCaducados
                    ? "No hay suficiente " + producto.getNombre() + " vigente: el resto de la existencia está caducada y no se puede vender."
                    : "Existencia insuficiente para: " + producto.getNombre());
        }
        if (producto.getStockActual().compareTo(cantidad) < 0)
            throw error(HttpStatus.CONFLICT, "Existencia insuficiente para: " + producto.getNombre());
        producto.setStockActual(producto.getStockActual().subtract(cantidad));
        productos.save(producto);
        for (Consumo c : usados) {
            movimientos.save(movimiento(producto, operador, tipo, c.cantidad().negate(),
                    motivo + " · lote " + c.numeroLote(), referencia, c.loteId(), ventaId));
        }
        return usados;
    }

    /** Ajuste de un lote (merma, daño, caducidad o conteo físico). Nunca deja existencias negativas. */
    @Transactional
    public Lote ajustarLote(Integer loteId, BigDecimal cantidad, String motivo, Usuario autor) {
        if (cantidad == null || cantidad.signum() == 0) throw error(HttpStatus.BAD_REQUEST, "La cantidad del ajuste no puede ser cero.");
        if (cantidad.scale() > 2) throw error(HttpStatus.BAD_REQUEST, "Use como máximo dos decimales.");
        String razon = requerido(motivo, "el motivo del ajuste", 200);
        Lote lote = lotes.findByIdForUpdate(loteId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Lote no encontrado."));
        Producto producto = productos.findByIdForUpdate(lote.getProducto().getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Producto no encontrado."));
        BigDecimal nuevoLote = lote.getCantidad().add(cantidad);
        BigDecimal nuevoProducto = producto.getStockActual().add(cantidad);
        if (nuevoLote.signum() < 0 || nuevoProducto.signum() < 0)
            throw error(HttpStatus.CONFLICT, "El ajuste dejaría el lote " + lote.getNumeroLote() + " en negativo (hay "
                    + lote.getCantidad().stripTrailingZeros().toPlainString() + ").");
        lote.setCantidad(nuevoLote);
        producto.setStockActual(nuevoProducto);
        productos.save(producto);
        movimientos.save(movimiento(producto, autor, MovimientoInventario.Tipo.AJUSTE, cantidad,
                razon + " · lote " + lote.getNumeroLote(), null, lote.getId(), null));
        auditoria.registrar(autor.getId(), "AJUSTE_LOTE", "lote", loteId, cantidad.toPlainString() + " · " + razon);
        return lote;
    }

    // ------------------------------------------------------------------ consultas
    public record AlertaCaducidad(Integer loteId, Integer productoId, String producto, String numeroLote,
            LocalDate caducidad, long diasRestantes, BigDecimal cantidad, boolean vencido, String proveedor) {}
    public record Reposicion(Integer productoId, String producto, BigDecimal existencia, BigDecimal minimo,
            BigDecimal sugerido, BigDecimal costoUnitario, BigDecimal costoEstimado, String ultimoProveedor) {}
    public record Margen(Integer productoId, String producto, BigDecimal precio, BigDecimal costo, BigDecimal margen,
            BigDecimal margenPorcentaje) {}

    @Transactional(readOnly = true)
    public List<AlertaCaducidad> caducidades(int dias) {
        LocalDate hoy = reloj.hoy();
        return lotes.consultar(null, null, hoy.plusDays(Math.max(0, Math.min(dias, 730))), true).stream()
                .map(l -> new AlertaCaducidad(l.getId(), l.getProducto().getId(), l.getProducto().getNombre(), l.getNumeroLote(),
                        l.getCaducidad(), java.time.temporal.ChronoUnit.DAYS.between(hoy, l.getCaducidad()), l.getCantidad(),
                        l.vencido(hoy), l.getProveedor() == null ? null : l.getProveedor().getNombre())).toList();
    }

    /** Propuesta de reposición: lleva cada producto al doble de su mínimo. */
    @Transactional(readOnly = true)
    public List<Reposicion> reposicion() {
        Map<Integer, String> ultimoProveedor = new HashMap<>();
        lotes.consultar(null, null, null, false).stream()
                .sorted(Comparator.comparing(Lote::getFechaRecepcion))
                .forEach(l -> { if (l.getProveedor() != null) ultimoProveedor.put(l.getProducto().getId(), l.getProveedor().getNombre()); });
        List<Reposicion> lista = new ArrayList<>();
        for (Producto p : productos.findAlertasStock()) {
            BigDecimal objetivo = p.getStockMinimo().multiply(BigDecimal.valueOf(2));
            BigDecimal sugerido = objetivo.subtract(p.getStockActual()).max(BigDecimal.ONE).setScale(0, RoundingMode.CEILING);
            BigDecimal costo = p.getCostoUltimo();
            lista.add(new Reposicion(p.getId(), p.getNombre(), p.getStockActual(), p.getStockMinimo(), sugerido, costo,
                    costo == null ? null : costo.multiply(sugerido).setScale(2, RoundingMode.HALF_UP), ultimoProveedor.get(p.getId())));
        }
        return lista;
    }

    /** Margen de venta con el costo promedio de los lotes disponibles o, si no hay, el último costo. */
    @Transactional(readOnly = true)
    public List<Margen> margenes() {
        Map<Integer, BigDecimal[]> acumulado = new HashMap<>();
        for (Lote l : lotes.consultar(null, null, null, true)) {
            if (l.getCostoUnitario() == null) continue;
            BigDecimal[] a = acumulado.computeIfAbsent(l.getProducto().getId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(l.getCostoUnitario().multiply(l.getCantidad()));
            a[1] = a[1].add(l.getCantidad());
        }
        List<Margen> lista = new ArrayList<>();
        for (Producto p : productos.findAll(org.springframework.data.domain.Sort.by("nombre"))) {
            if (!p.isActivo() || !"PRODUCTO".equals(p.getTipo())) continue;
            BigDecimal[] a = acumulado.get(p.getId());
            BigDecimal costo = a != null && a[1].signum() > 0 ? a[0].divide(a[1], 2, RoundingMode.HALF_UP) : p.getCostoUltimo();
            if (costo == null) continue;
            BigDecimal margen = p.getPrecio().subtract(costo);
            BigDecimal porcentaje = p.getPrecio().signum() == 0 ? BigDecimal.ZERO
                    : margen.multiply(BigDecimal.valueOf(100)).divide(p.getPrecio(), 1, RoundingMode.HALF_UP);
            lista.add(new Margen(p.getId(), p.getNombre(), p.getPrecio(), costo, margen, porcentaje));
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public List<Lote> lotes(Integer productoId, Integer proveedorId, LocalDate caducaAntes, boolean conExistencia) {
        return lotes.consultar(productoId, proveedorId, caducaAntes, conExistencia);
    }

    // ------------------------------------------------------------------ apoyo
    static MovimientoInventario movimiento(Producto p, Usuario u, MovimientoInventario.Tipo tipo, BigDecimal cantidad,
            String motivo, String referencia, Integer loteId, Integer ventaId) {
        MovimientoInventario m = new MovimientoInventario();
        m.setProducto(p); m.setUsuario(u); m.setTipo(tipo); m.setCantidad(cantidad);
        m.setMotivo(motivo == null || motivo.length() <= 200 ? motivo : motivo.substring(0, 200));
        m.setReferencia(referencia); m.setLoteId(loteId); m.setVentaId(ventaId);
        return m;
    }
    private static BigDecimal cantidadValida(BigDecimal c) {
        if (c == null || c.signum() <= 0) throw error(HttpStatus.BAD_REQUEST, "Las cantidades deben ser mayores a cero.");
        if (c.scale() > 2 || c.compareTo(new BigDecimal("99999999")) > 0) throw error(HttpStatus.BAD_REQUEST, "Cantidad fuera de rango.");
        return c;
    }
    private static BigDecimal dinero(BigDecimal v, String que) {
        if (v == null || v.signum() < 0) throw error(HttpStatus.BAD_REQUEST, "Capture " + que + " (no puede ser negativo).");
        if (v.scale() > 2 || v.compareTo(new BigDecimal("99999999")) > 0) throw error(HttpStatus.BAD_REQUEST, "Importe fuera de rango.");
        return v;
    }
    private static boolean vacio(String s) { return s == null || s.isBlank(); }
    private static String requerido(String t, String que, int max) {
        if (vacio(t)) throw error(HttpStatus.BAD_REQUEST, "Capture " + que + ".");
        String v = t.trim();
        if (v.length() > max) throw error(HttpStatus.BAD_REQUEST, "El texto de " + que + " es demasiado largo.");
        return v;
    }
    private static String opcional(String t, int max) {
        if (vacio(t)) return null;
        String v = t.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
    private static ResponseStatusException error(HttpStatus s, String m) { return new ResponseStatusException(s, m); }
}
