package mx.buap.veterinaria;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.comun.*;
import mx.buap.veterinaria.farmacia.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

/** Compras con lote, caducidad, FEFO y ajustes por lote. */
class FarmaciaServiceTest {
    final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    final CompraRepository compras = mock(CompraRepository.class);
    final LoteRepository lotes = mock(LoteRepository.class);
    final ProductoRepository productos = mock(ProductoRepository.class);
    final MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
    final Reloj reloj = new Reloj();
    final FarmaciaService farmacia = new FarmaciaService(proveedores, compras, lotes, productos, movimientos,
            mock(AuditoriaService.class), reloj);
    static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    Usuario admin;
    Proveedor proveedor;
    Producto vacuna;

    @BeforeEach void preparar() {
        reloj.fijar(Clock.fixed(HOY.atTime(10, 0).atZone(Reloj.ZONA).toInstant(), Reloj.ZONA));
        admin = mock(Usuario.class); when(admin.getId()).thenReturn(1);
        proveedor = new Proveedor(); proveedor.setNombre("Distribuidora Norte");
        when(proveedores.findById(5)).thenReturn(Optional.of(proveedor));
        vacuna = producto("Vacuna múltiple", true, "0");
        when(productos.findByIdForUpdate(10)).thenReturn(Optional.of(vacuna));
        when(compras.save(any())).thenAnswer(i -> i.getArgument(0));
        when(lotes.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    static Producto producto(String nombre, boolean caducidad, String existencia) {
        Producto p = new Producto();
        p.setNombre(nombre); p.setTipo("PRODUCTO"); p.setPrecio(new BigDecimal("280")); p.setActivo(true);
        p.setManejaCaducidad(caducidad); p.setStockActual(new BigDecimal(existencia)); p.setStockMinimo(new BigDecimal("2"));
        return p;
    }
    static Lote lote(Producto p, int id, String numero, LocalDate caducidad, String cantidad) {
        Lote l = mock(Lote.class, CALLS_REAL_METHODS);
        doReturn(id).when(l).getId();
        l.setProducto(p); l.setNumeroLote(numero); l.setCaducidad(caducidad); l.setCantidad(new BigDecimal(cantidad));
        return l;
    }

    @Test void compraConLoteSumaExistenciaYRegistraEntrada() {
        Compra c = farmacia.registrarCompra(5, "FAC-123", null, List.of(new FarmaciaService.RenglonCompra(
                10, "L-01", HOY.plusMonths(6), new BigDecimal("12"), new BigDecimal("150.50"))), admin);
        assertEquals(0, new BigDecimal("12").compareTo(vacuna.getStockActual()));
        assertEquals(0, new BigDecimal("1806.00").compareTo(c.getTotal()));
        assertEquals(0, new BigDecimal("150.50").compareTo(vacuna.getCostoUltimo()));
        ArgumentCaptor<MovimientoInventario> m = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientos).save(m.capture());
        assertEquals(MovimientoInventario.Tipo.ENTRADA, m.getValue().getTipo());
    }

    @Test void rechazaLoteCaducadoOSinDatos() {
        assertThrows(ResponseStatusException.class, () -> farmacia.registrarCompra(5, null, null, List.of(
                new FarmaciaService.RenglonCompra(10, "L-02", HOY, BigDecimal.ONE, BigDecimal.TEN)), admin));
        assertThrows(ResponseStatusException.class, () -> farmacia.registrarCompra(5, null, null, List.of(
                new FarmaciaService.RenglonCompra(10, null, HOY.plusDays(90), BigDecimal.ONE, BigDecimal.TEN)), admin));
        assertThrows(ResponseStatusException.class, () -> farmacia.registrarCompra(5, null, null, List.of(
                new FarmaciaService.RenglonCompra(10, "L-03", HOY.plusDays(90), new BigDecimal("-1"), BigDecimal.TEN)), admin));
        assertEquals(0, BigDecimal.ZERO.compareTo(vacuna.getStockActual()));
    }

    @Test void fefoUsaPrimeroElLoteQueVenceAntesYOmiteCaducados() {
        vacuna.setStockActual(new BigDecimal("13"));
        Lote vencido = lote(vacuna, 1, "VIEJO", HOY.minusDays(1), "3");
        Lote pronto = lote(vacuna, 2, "PRONTO", HOY.plusDays(20), "4");
        Lote tarde = lote(vacuna, 3, "TARDE", HOY.plusDays(200), "6");
        when(lotes.paraConsumir(any())).thenReturn(List.of(vencido, pronto, tarde));
        List<FarmaciaService.Consumo> usados = farmacia.consumirFefo(vacuna, new BigDecimal("5"), admin,
                MovimientoInventario.Tipo.VENTA, "Venta 9", "V-1", 9);
        assertEquals(List.of("PRONTO", "TARDE"), usados.stream().map(FarmaciaService.Consumo::numeroLote).toList());
        assertEquals(0, new BigDecimal("3").compareTo(vencido.getCantidad()), "El lote caducado no se toca");
        assertEquals(0, BigDecimal.ZERO.compareTo(pronto.getCantidad()));
        assertEquals(0, new BigDecimal("5").compareTo(tarde.getCantidad()));
        assertEquals(0, new BigDecimal("8").compareTo(vacuna.getStockActual()));
        verify(movimientos, times(2)).save(argThat(m -> m.getLoteId() != null && m.getVentaId() == 9));
    }

    @Test void bloqueaVentaCuandoSoloQuedaExistenciaCaducada() {
        vacuna.setStockActual(new BigDecimal("3"));
        Lote viejo = lote(vacuna, 1, "VIEJO", HOY.minusDays(1), "3");
        when(lotes.paraConsumir(any())).thenReturn(List.of(viejo));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> farmacia.consumirFefo(vacuna,
                BigDecimal.ONE, admin, MovimientoInventario.Tipo.VENTA, "Venta", "V-2", 2));
        assertTrue(e.getReason().contains("caducada"));
        assertEquals(0, new BigDecimal("3").compareTo(vacuna.getStockActual()));
        verify(movimientos, never()).save(any());
    }

    @Test void ajusteDeLoteNoDejaNegativos() {
        vacuna.setStockActual(new BigDecimal("4"));
        Lote l = lote(vacuna, 7, "L-07", HOY.plusDays(30), "4");
        when(lotes.findByIdForUpdate(7)).thenReturn(Optional.of(l));
        when(productos.findByIdForUpdate(any())).thenReturn(Optional.of(vacuna));
        assertThrows(ResponseStatusException.class, () -> farmacia.ajustarLote(7, new BigDecimal("-5"), "Merma", admin));
        assertThrows(ResponseStatusException.class, () -> farmacia.ajustarLote(7, new BigDecimal("-1"), " ", admin));
        farmacia.ajustarLote(7, new BigDecimal("-1"), "Frasco roto", admin);
        assertEquals(0, new BigDecimal("3").compareTo(l.getCantidad()));
        assertEquals(0, new BigDecimal("3").compareTo(vacuna.getStockActual()));
    }

    @Test void disponibleParaVentaSoloCuentaLotesVigentes() {
        when(lotes.disponibleVigente(any(), eq(HOY))).thenReturn(new BigDecimal("4"));
        vacuna.setStockActual(new BigDecimal("7"));
        assertEquals(0, new BigDecimal("4").compareTo(farmacia.disponibleParaVenta(vacuna)));
        Producto croquetas = producto("Croquetas", false, "9");
        assertEquals(0, new BigDecimal("9").compareTo(farmacia.disponibleParaVenta(croquetas)));
    }

    @Test void reposicionLlevaAlDobleDelMinimo() {
        Producto p = producto("Desparasitante", false, "1");
        p.setStockMinimo(new BigDecimal("5")); p.setCostoUltimo(new BigDecimal("20"));
        when(productos.findAlertasStock()).thenReturn(List.of(p));
        when(lotes.consultar(any(), any(), any(), anyBoolean())).thenReturn(List.of());
        FarmaciaService.Reposicion r = farmacia.reposicion().get(0);
        assertEquals(0, new BigDecimal("9").compareTo(r.sugerido()));
        assertEquals(0, new BigDecimal("180.00").compareTo(r.costoEstimado()));
    }
}
