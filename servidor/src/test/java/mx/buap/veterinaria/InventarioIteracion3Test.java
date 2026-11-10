package mx.buap.veterinaria;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.dto.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.service.InventarioService;

/** Iteración 3: ajustes de inventario, alertas de stock mínimo y kardex. */
class InventarioIteracion3Test {
    final ProductoRepository productos = mock(ProductoRepository.class);
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
    final InventarioService service = new InventarioService(productos, usuarios, movimientos);

    Producto producto(String tipo, String existencia, String minimo) {
        Producto p = new Producto();
        p.setNombre("Croquetas 2 kg"); p.setTipo(tipo); p.setPrecio(new BigDecimal("389"));
        p.setStockActual(new BigDecimal(existencia)); p.setStockMinimo(new BigDecimal(minimo));
        when(productos.findByIdForUpdate(7)).thenReturn(Optional.of(p));
        when(productos.findById(7)).thenReturn(Optional.of(p));
        return p;
    }

    void operadorActivo() {
        Usuario admin = mock(Usuario.class);
        when(admin.isActivo()).thenReturn(true);
        when(admin.getNombre()).thenReturn("Administrador");
        when(usuarios.findById(1)).thenReturn(Optional.of(admin));
        when(movimientos.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test void ajusteNegativoRestaYRegistraMovimiento() {
        Producto p = producto("PRODUCTO", "10", "3"); operadorActivo();
        MovimientoInventario m = service.ajuste(7, new AjusteRequest(new BigDecimal("-2"), 1, "Merma: bolsa rota"));
        assertEquals(0, new BigDecimal("8").compareTo(p.getStockActual()));
        assertEquals(MovimientoInventario.Tipo.AJUSTE, m.getTipo());
        assertEquals(0, new BigDecimal("-2").compareTo(m.getCantidad()));
        verify(productos).save(p);
    }

    @Test void ajusteNoDejaExistenciaNegativa() {
        Producto p = producto("PRODUCTO", "1", "3"); operadorActivo();
        assertThrows(ResponseStatusException.class,
                () -> service.ajuste(7, new AjusteRequest(new BigDecimal("-5"), 1, "Conteo físico")));
        assertEquals(0, BigDecimal.ONE.compareTo(p.getStockActual()));
        verifyNoInteractions(movimientos);
    }

    @Test void ajusteRechazaCeroYServicios() {
        producto("SERVICIO", "0", "0"); operadorActivo();
        assertThrows(ResponseStatusException.class,
                () -> service.ajuste(7, new AjusteRequest(BigDecimal.ZERO, 1, "Nada")));
        assertThrows(ResponseStatusException.class,
                () -> service.ajuste(7, new AjusteRequest(BigDecimal.ONE, 1, "Consulta")));
        verifyNoInteractions(movimientos);
    }

    @Test void alertaCalculaFaltante() {
        Producto p = producto("PRODUCTO", "2", "5");
        when(productos.findAlertasStock()).thenReturn(List.of(p));
        AlertaStock alerta = service.alertas().get(0);
        assertEquals(0, new BigDecimal("3").compareTo(alerta.faltante()));
    }

    @Test void kardexReconstruyeSaldoDesdeExistenciaActual() {
        Producto p = producto("PRODUCTO", "8", "3"); operadorActivo();
        MovimientoInventario entrada = service.nuevo(p, usuarios.findById(1).get(),
                MovimientoInventario.Tipo.ENTRADA, new BigDecimal("10"), "Compra");
        MovimientoInventario venta = service.nuevo(p, usuarios.findById(1).get(),
                MovimientoInventario.Tipo.VENTA, new BigDecimal("-2"), "Venta");
        when(movimientos.findByProductoIdOrderByFechaDesc(7)).thenReturn(List.of(venta, entrada));
        List<MovimientoKardex> kardex = service.kardex(7);
        assertEquals(0, new BigDecimal("8").compareTo(kardex.get(0).saldo()));
        assertEquals(0, new BigDecimal("10").compareTo(kardex.get(1).saldo()));
        assertEquals("Administrador", kardex.get(0).usuario());
    }
}
