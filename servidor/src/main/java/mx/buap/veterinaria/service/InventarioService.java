package mx.buap.veterinaria.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.dto.AjusteRequest;
import mx.buap.veterinaria.dto.AlertaStock;
import mx.buap.veterinaria.dto.MovimientoKardex;
import mx.buap.veterinaria.dto.MovimientoRequest;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

@Service
public class InventarioService {
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final MovimientoInventarioRepository movimientos;
    public InventarioService(ProductoRepository p, UsuarioRepository u, MovimientoInventarioRepository m) {
        productos=p; usuarios=u; movimientos=m;
    }
    @Transactional
    public MovimientoInventario entrada(Integer productoId, MovimientoRequest request) {
        Producto producto = productoFisico(productoId);
        Usuario usuario = operador(request.usuarioId());
        producto.setStockActual(producto.getStockActual().add(request.cantidad()));
        productos.save(producto);
        MovimientoInventario m = nuevo(producto, usuario, MovimientoInventario.Tipo.ENTRADA,
                request.cantidad(), request.motivo());
        return movimientos.save(m);
    }

    /**
     * Iteración 3: corrige la existencia por merma, caducidad o conteo físico.
     * La cantidad lleva signo; el ajuste y su movimiento se guardan juntos o no se guarda nada.
     */
    @Transactional
    public MovimientoInventario ajuste(Integer productoId, AjusteRequest request) {
        if (request.cantidad().signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad del ajuste no puede ser cero");
        }
        Producto producto = productoFisico(productoId);
        Usuario usuario = operador(request.usuarioId());
        BigDecimal nuevaExistencia = producto.getStockActual().add(request.cantidad());
        if (nuevaExistencia.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El ajuste dejaría la existencia de "
                    + producto.getNombre() + " en negativo (hay "
                    + producto.getStockActual().stripTrailingZeros().toPlainString() + ")");
        }
        producto.setStockActual(nuevaExistencia);
        productos.save(producto);
        MovimientoInventario m = nuevo(producto, usuario, MovimientoInventario.Tipo.AJUSTE,
                request.cantidad(), request.motivo().trim());
        return movimientos.save(m);
    }

    /** RF-14: productos que ya llegaron a su stock mínimo. */
    @Transactional(readOnly=true)
    public List<AlertaStock> alertas() {
        return productos.findAlertasStock().stream().map(AlertaStock::de).toList();
    }

    @Transactional(readOnly=true)
    public List<MovimientoInventario> historial(Integer productoId) {
        return movimientos.findByProductoIdOrderByFechaDesc(productoId);
    }

    /**
     * Kardex del producto, del movimiento más reciente al más antiguo. El saldo se reconstruye
     * desde la existencia actual hacia atrás, así siempre coincide con lo que hay en el sistema.
     */
    @Transactional(readOnly=true)
    public List<MovimientoKardex> kardex(Integer productoId) {
        Producto producto = productos.findById(productoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        BigDecimal saldo = producto.getStockActual();
        List<MovimientoKardex> renglones = new ArrayList<>();
        for (MovimientoInventario m : movimientos.findByProductoIdOrderByFechaDesc(productoId)) {
            String usuario = m.getUsuario() == null ? "" : m.getUsuario().getNombre();
            renglones.add(new MovimientoKardex(m.getId(), m.getFecha(), m.getTipo().name(), m.getCantidad(),
                    saldo, m.getMotivo(), m.getReferencia(), usuario));
            saldo = saldo.subtract(m.getCantidad());
        }
        return renglones;
    }

    private Producto productoFisico(Integer productoId) {
        Producto producto = productos.findByIdForUpdate(productoId).filter(Producto::isActivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (!"PRODUCTO".equals(producto.getTipo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los servicios no manejan existencias");
        }
        return producto;
    }
    private Usuario operador(Integer id) {
        return usuarios.findById(id).filter(Usuario::isActivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Operador inactivo o inexistente"));
    }
    public MovimientoInventario nuevo(Producto p, Usuario u, MovimientoInventario.Tipo tipo, BigDecimal cantidad, String motivo) {
        MovimientoInventario m = new MovimientoInventario(); m.setProducto(p); m.setUsuario(u);
        m.setTipo(tipo); m.setCantidad(cantidad); m.setMotivo(motivo); return m;
    }
}
