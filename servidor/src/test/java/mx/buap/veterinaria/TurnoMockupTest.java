package mx.buap.veterinaria;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.service.*;
import mx.buap.veterinaria.dto.*;

class TurnoMockupTest {
 final TurnoRepository turnos=mock(TurnoRepository.class);
 final PagoConsultaRepository pagos=mock(PagoConsultaRepository.class);
 final SalidaEfectivoRepository salidas=mock(SalidaEfectivoRepository.class);
 final AutorizacionService auth=mock(AutorizacionService.class);
 final VentaRepository ventas=mock(VentaRepository.class);
 final TurnoService service=new TurnoService(turnos,mock(UsuarioRepository.class),pagos,salidas,auth,mock(BitacoraRepository.class),ventas,new ObjectMapper().findAndRegisterModules());
 Turno preparar() throws Exception {
  Usuario cajero=new Usuario();cajero.setNombre("Cajero prueba");var u=Usuario.class.getDeclaredField("id");u.setAccessible(true);u.set(cajero,42);
  Turno t=new Turno();t.setCajero(cajero);t.setFondoInicial(new BigDecimal("500"));var id=Turno.class.getDeclaredField("id");id.setAccessible(true);id.set(t,1);
  when(turnos.findOpenForUpdate()).thenReturn(Optional.of(t));when(turnos.findByCierreIsNull()).thenReturn(Optional.of(t));
  when(pagos.totalDelTurno(1,Pago.Forma.EFECTIVO)).thenReturn(new BigDecimal("6345"));
  when(pagos.totalDelTurno(1,Pago.Forma.TARJETA)).thenReturn(new BigDecimal("4250"));
  when(pagos.totalDelTurno(1,Pago.Forma.TRANSFERENCIA)).thenReturn(BigDecimal.ZERO);
  when(salidas.findByTurnoIdOrderByFecha(1)).thenReturn(List.of(new SalidaEfectivo(1,"Agua",new BigDecimal("45"),9)));
  return t;
 }
 @Test void corteDescuentaRetirosYSeparaPagosNoEfectivos() throws Exception {
  Turno t=preparar();var r=service.cerrar(new TurnoCierreRequest(new BigDecimal("6800"),Map.of("100",68),new BigDecimal("4250"),BigDecimal.ZERO),42);
  assertEquals(0,((BigDecimal)r.get("efectivoEsperado")).compareTo(new BigDecimal("6800")));
  assertEquals(0,((BigDecimal)r.get("diferencia")).signum());assertNotNull(t.getCierre());
  verify(auth).registrar(eq(42),isNull(),eq("CORTE"),eq("turno"),eq(1),contains("denominaciones"));
 }
 @Test void turnoAbiertoNoRevelaSaldoEsperado() throws Exception {
  preparar();var r=service.abierto();assertFalse(r.containsKey("efectivoEsperado"));verifyNoInteractions(pagos);
 }
 @Test void rechazaConteoInconsistenteYTurnoDeOtroCajero() throws Exception {
  Turno t=preparar();assertThrows(ResponseStatusException.class,() -> service.cerrar(new TurnoCierreRequest(new BigDecimal("6800"),Map.of("100",67),null,null),42));
  assertNull(t.getCierre());assertThrows(ResponseStatusException.class,() -> service.propioAbierto(99));
  verifyNoInteractions(auth);
 }
}
