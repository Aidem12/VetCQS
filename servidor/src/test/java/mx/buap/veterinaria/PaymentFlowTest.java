package mx.buap.veterinaria;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.dto.*;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.service.*;
class PaymentFlowTest {
 final VentaRepository ventas=mock(VentaRepository.class);
 final ProductoRepository productos=mock(ProductoRepository.class);
 final PagoRepository pagos=mock(PagoRepository.class);
 final TurnoRepository turnos=mock(TurnoRepository.class);
 final VentaService service=new VentaService(ventas,productos,pagos,mock(ConsultaRepository.class),mock(MovimientoInventarioRepository.class),mock(UsuarioRepository.class),turnos);
 Venta preparar() throws Exception {
  Usuario cajero=mock(Usuario.class);when(cajero.getId()).thenReturn(42);Turno turno=new Turno();turno.setCajero(cajero);
  when(turnos.findOpenForUpdate()).thenReturn(Optional.of(turno));
  Venta v=new Venta();v.setTotal(new BigDecimal("100"));when(ventas.findByIdForUpdate(1)).thenReturn(Optional.of(v));when(ventas.save(any())).thenAnswer(i->i.getArgument(0));return v;
 }
 @Test void aceptaPagoMixtoExacto() throws Exception {
  Venta v=preparar();service.cobrar(1,List.of(new PagoRequest(Pago.Forma.EFECTIVO,new BigDecimal("40"),new BigDecimal("50")),new PagoRequest(Pago.Forma.TARJETA,new BigDecimal("30"),null),new PagoRequest(Pago.Forma.TRANSFERENCIA,new BigDecimal("30"),null)),42);
  assertEquals(Venta.Estado.PAGADA,v.getEstado());verify(pagos,times(3)).save(any());assertNotNull(v.getTurno());
 }
 @Test void rechazaSumaIncorrectaAntesDeGuardarPagos() throws Exception {
  preparar();assertThrows(ResponseStatusException.class,()->service.cobrar(1,List.of(new PagoRequest(Pago.Forma.TARJETA,new BigDecimal("99"),null)),42));verifyNoInteractions(pagos);
 }
 @Test void noCobraDosVecesNiDesdeOtroCajero() throws Exception {
  Venta v=preparar();var pago=List.of(new PagoRequest(Pago.Forma.EFECTIVO,new BigDecimal("100"),new BigDecimal("100")));
  assertThrows(ResponseStatusException.class,()->service.cobrar(1,pago,99));
  v.setEstado(Venta.Estado.PAGADA);assertThrows(ResponseStatusException.class,()->service.cobrar(1,pago,42));verifyNoInteractions(pagos);
 }
}
