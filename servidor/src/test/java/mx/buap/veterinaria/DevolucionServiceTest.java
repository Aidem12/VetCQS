package mx.buap.veterinaria;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.comun.Reloj;
import mx.buap.veterinaria.devolucion.*;
import mx.buap.veterinaria.farmacia.LoteRepository;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.service.*;

/** Devoluciones: límites por lo vendido, prorrateo del descuento, efectivo del turno y reingreso a inventario. */
class DevolucionServiceTest {
    final DevolucionRepository devoluciones = mock(DevolucionRepository.class);
    final VentaRepository ventas = mock(VentaRepository.class);
    final ProductoRepository productos = mock(ProductoRepository.class);
    final MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final AutorizacionService autorizacion = mock(AutorizacionService.class);
    final TurnoService turnos = mock(TurnoService.class);
    final Reloj reloj = new Reloj();
    final DevolucionService servicio = new DevolucionService(devoluciones, ventas, productos, movimientos,
            mock(LoteRepository.class), usuarios, autorizacion, turnos, reloj);
    Venta venta; Producto croquetas; Producto consulta; Turno turno;

    @BeforeEach void preparar() {
        reloj.fijar(Clock.fixed(LocalDate.of(2026, 11, 18).atTime(11, 0).atZone(Reloj.ZONA).toInstant(), Reloj.ZONA));
        croquetas = producto(1, "Croquetas", "PRODUCTO", "5");
        consulta = producto(2, "Consulta", "SERVICIO", "0");
        venta = mock(Venta.class);
        when(venta.getId()).thenReturn(9); when(venta.getFolio()).thenReturn("V-9");
        when(venta.getEstado()).thenReturn(Venta.Estado.PAGADA);
        // Subtotal 500 con 10 % de descuento: total 450.
        when(venta.getSubtotal()).thenReturn(new BigDecimal("500.00")); when(venta.getTotal()).thenReturn(new BigDecimal("450.00"));
        when(venta.getDetalles()).thenReturn(List.of(detalle(croquetas, "2", "75.00"), detalle(consulta, "1", "350.00")));
        when(ventas.findByIdForUpdate(9)).thenReturn(Optional.of(venta));
        turno = mock(Turno.class); when(turno.getId()).thenReturn(4);
        when(turnos.propioAbierto(5)).thenReturn(turno);
        when(turnos.efectivoDisponible(turno)).thenReturn(new BigDecimal("1000"));
        Usuario admin = mock(Usuario.class); when(admin.getId()).thenReturn(1);
        when(autorizacion.validar("admin", "clave-prueba")).thenReturn(admin);
        when(usuarios.findById(5)).thenReturn(Optional.of(mock(Usuario.class)));
        when(devoluciones.devuelto(anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);
        when(devoluciones.reembolsado(9)).thenReturn(BigDecimal.ZERO);
        when(devoluciones.siguienteFolio()).thenReturn(7L);
        when(devoluciones.save(any())).thenAnswer(i -> i.getArgument(0));
        when(productos.findByIdForUpdate(1)).thenReturn(Optional.of(croquetas));
    }
    static Producto producto(int id, String nombre, String tipo, String existencia) {
        Producto p = mock(Producto.class, CALLS_REAL_METHODS);
        doReturn(id).when(p).getId();
        p.setNombre(nombre); p.setTipo(tipo); p.setStockActual(new BigDecimal(existencia)); p.setManejaCaducidad(false);
        return p;
    }
    static VentaDetalle detalle(Producto p, String cantidad, String precio) {
        VentaDetalle d = new VentaDetalle(); d.setProducto(p); d.setCantidad(new BigDecimal(cantidad)); d.setPrecioUnitario(new BigDecimal(precio));
        return d;
    }
    DevolucionService.Solicitud solicitud(String cantidad, Devolucion.Forma forma) {
        return new DevolucionService.Solicitud(List.of(new DevolucionService.Renglon(1, new BigDecimal(cantidad), true)),
                "Bolsa dañada", forma, "admin", "clave-prueba");
    }

    @Test void prorrateaDescuentoYReingresaAlInventario() {
        Devolucion d = servicio.registrar(9, solicitud("1", Devolucion.Forma.EFECTIVO), 5);
        assertEquals(0, new BigDecimal("67.50").compareTo(d.getMonto()), "75 menos el 10 % de descuento");
        assertEquals("D-000007", d.getFolio());
        assertEquals(4, d.getTurnoId());
        assertEquals(0, new BigDecimal("6").compareTo(croquetas.getStockActual()));
        verify(movimientos).save(argThat(m -> m.getTipo() == MovimientoInventario.Tipo.DEVOLUCION && m.getVentaId() == 9));
        verify(autorizacion).registrar(eq(5), eq(1), eq("DEVOLUCION"), eq("devolucion"), any(), contains("D-000007"));
    }

    @Test void noPermiteDevolverMasDeLoVendido() {
        when(devoluciones.devuelto(9, 1)).thenReturn(new BigDecimal("1"));
        var e = assertThrows(ResponseStatusException.class, () -> servicio.registrar(9, solicitud("2", Devolucion.Forma.TARJETA), 5));
        assertTrue(e.getReason().contains("disponible: 1"));
        verify(devoluciones, never()).save(any());
        assertEquals(0, new BigDecimal("5").compareTo(croquetas.getStockActual()));
    }

    @Test void exigeEfectivoSuficienteYVentaPagada() {
        when(turnos.efectivoDisponible(turno)).thenReturn(new BigDecimal("10"));
        assertThrows(ResponseStatusException.class, () -> servicio.registrar(9, solicitud("1", Devolucion.Forma.EFECTIVO), 5));
        when(venta.getEstado()).thenReturn(Venta.Estado.CANCELADA);
        assertThrows(ResponseStatusException.class, () -> servicio.registrar(9, solicitud("1", Devolucion.Forma.TARJETA), 5));
        verify(devoluciones, never()).save(any());
    }

    @Test void requiereMotivoYAutorizacion() {
        assertThrows(ResponseStatusException.class, () -> servicio.registrar(9, new DevolucionService.Solicitud(
                List.of(new DevolucionService.Renglon(1, BigDecimal.ONE, true)), " ", Devolucion.Forma.TARJETA, "admin", "clave-prueba"), 5));
        when(autorizacion.validar("admin", "mala")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class, () -> servicio.registrar(9, new DevolucionService.Solicitud(
                List.of(new DevolucionService.Renglon(1, BigDecimal.ONE, true)), "Motivo", Devolucion.Forma.TARJETA, "admin", "mala"), 5));
        verify(devoluciones, never()).save(any());
    }

    @Test void serviciosNoReingresanInventario() {
        Devolucion d = servicio.registrar(9, new DevolucionService.Solicitud(List.of(new DevolucionService.Renglon(2, BigDecimal.ONE, true)),
                "Consulta no realizada", Devolucion.Forma.TRANSFERENCIA, "admin", "clave-prueba"), 5);
        assertEquals(0, new BigDecimal("315.00").compareTo(d.getMonto()));
        assertFalse(d.getDetalles().get(0).isReingresa());
        verify(movimientos, never()).save(any());
    }
}
