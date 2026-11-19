package mx.buap.veterinaria;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import mx.buap.veterinaria.controller.FacturaController;
import mx.buap.veterinaria.repository.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.dto.*;
import org.springframework.web.server.ResponseStatusException;

class EstabilizacionTest {
 @Test void idempotenciaNoDevuelveFacturaDeOtraVenta() {
  var ventas=mock(VentaRepository.class);var facturas=mock(FacturaRepository.class);
  var venta=mock(Venta.class); when(venta.getId()).thenReturn(1);
  var factura=new Factura();factura.setVenta(venta);
  when(ventas.findByIdForUpdate(2)).thenReturn(Optional.of(mock(Venta.class)));
  when(facturas.findByIdempotencia("clave")).thenReturn(Optional.of(factura));
  var controller=new FacturaController(facturas,ventas);
  var err=assertThrows(ResponseStatusException.class,()->controller.crear(2,new FacturaRequest("XAXX010101000","Prueba","616","72000","S01","clave")));
  assertEquals(409,err.getStatusCode().value()); verify(facturas,never()).save(any());
 }
 @Test void cantidadesInvalidasSeRechazanAntesDePersistir() {
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
   var validator=factory.getValidator();
   for(String valor:List.of("-1","0","0.001","100000000")) {
    var request=new PrecuentaRequest(1,List.of(new PrecuentaRequest.Detalle(1,new BigDecimal(valor))));
    assertFalse(validator.validate(request).isEmpty(),valor);
   }
   assertTrue(validator.validate(new PrecuentaRequest(1,List.of(new PrecuentaRequest.Detalle(1,new BigDecimal("0.50"))))).isEmpty());
  }
 }
}
