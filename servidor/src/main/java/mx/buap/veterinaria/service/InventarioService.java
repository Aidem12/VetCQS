package mx.buap.veterinaria.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
        Producto producto = productos.findByIdForUpdate(productoId).filter(Producto::isActivo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        Usuario usuario = operador(request.usuarioId());
        producto.setStockActual(producto.getStockActual().add(request.cantidad()));
        productos.save(producto);
        MovimientoInventario m = nuevo(producto, usuario, MovimientoInventario.Tipo.ENTRADA,
                request.cantidad(), request.motivo());
        return movimientos.save(m);
    }
    @Transactional(readOnly=true)
    public List<MovimientoInventario> historial(Integer productoId) {
        return movimientos.findByProductoIdOrderByFechaDesc(productoId);
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
