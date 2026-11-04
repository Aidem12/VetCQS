package mx.buap.veterinaria;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.service.*;
import mx.buap.veterinaria.dto.*;
class AutorizacionVentaTest {
 final VentaRepository ventas=mock(VentaRepository.class);
 final AutorizacionService auth=mock(AutorizacionService.class);
 final VentaAutorizacionService service=new VentaAutorizacionService(ventas,auth);
 Venta preparar(){Usuario admin=mock(Usuario.class);when(admin.getId()).thenReturn(9);when(auth.validar("admin","prueba")).thenReturn(admin);Venta v=new Venta();v.setSubtotal(new BigDecimal("100"));v.setTotal(new BigDecimal("100"));when(ventas.findByIdForUpdate(1)).thenReturn(Optional.of(v));return v;}
 AutorizacionRequest request(String accion,BigDecimal importe){return new AutorizacionRequest("admin","prueba","Corrección autorizada",accion,null,importe);}
 @Test void descuentoActualizaTotalYRegistraAutorizador(){Venta v=preparar();service.aplicar(1,42,request("DESCUENTO",new BigDecimal("15")));assertEquals(new BigDecimal("85"),v.getTotal());verify(auth).registrar(eq(42),eq(9),eq("DESCUENTO"),eq("venta"),eq(1),contains("Total anterior: 100"));}
 @Test void cancelarPendienteNoPermiteCobrarla(){Venta v=preparar();service.aplicar(1,42,request("CANCELAR_VENTA",null));assertEquals(Venta.Estado.CANCELADA,v.getEstado());}
 @Test void impideModificarVentasPagadasYDescuentoExcesivo(){Venta v=preparar();assertThrows(ResponseStatusException.class,()->service.aplicar(1,42,request("DESCUENTO",new BigDecimal("101"))));v.setEstado(Venta.Estado.PAGADA);assertThrows(ResponseStatusException.class,()->service.aplicar(1,42,request("CANCELAR_VENTA",null)));verify(ventas,never()).save(any());}
}
