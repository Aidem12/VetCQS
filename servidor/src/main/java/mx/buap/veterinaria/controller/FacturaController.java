package mx.buap.veterinaria.controller;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import mx.buap.veterinaria.dto.FacturaRequest;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;
@RestController @RequestMapping("/api/facturas")
public class FacturaController {
 private final FacturaRepository facturas; private final VentaRepository ventas;
 public FacturaController(FacturaRepository f, VentaRepository v){facturas=f;ventas=v;}
 @org.springframework.transaction.annotation.Transactional
 @PostMapping("/venta/{ventaId}") @ResponseStatus(HttpStatus.CREATED)
 public Factura crear(@PathVariable Integer ventaId,@Valid @RequestBody FacturaRequest r){
  Venta v=ventas.findByIdForUpdate(ventaId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Venta no encontrada"));
  var existente=facturas.findByIdempotencia(r.idempotencia());
  if(existente.isPresent()) {
   if(!existente.get().getVenta().getId().equals(ventaId)) throw new ResponseStatusException(HttpStatus.CONFLICT,"La clave de idempotencia pertenece a otra venta");
   return existente.get();
  }
  if(facturas.findByVentaId(ventaId).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"La venta ya tiene una factura de demostración");
   if(v.getEstado()!=Venta.Estado.PAGADA) throw new ResponseStatusException(HttpStatus.CONFLICT,"La venta debe estar pagada");
   Factura f=new Factura(); f.setVenta(v); f.setRfcReceptor(r.rfcReceptor().toUpperCase()); f.setRazonSocial(r.razonSocial());
   f.setRegimenFiscal(r.regimenFiscal()); f.setCodigoPostal(r.codigoPostal()); f.setUsoCfdi(r.usoCfdi()); f.setIdempotencia(r.idempotencia());
   f.setEstado("TIMBRADA"); f.setUuidSat(UUID.nameUUIDFromBytes(("MOCK-CFDI-"+ventaId+"-"+r.idempotencia()).getBytes(StandardCharsets.UTF_8)));
   f.setRespuestaMock("{\"simulado\":true,\"ventaId\":"+ventaId+"}"); f.setMock(true); return facturas.save(f);
 }
 @GetMapping("/venta/{ventaId}") public Factura consultar(@PathVariable Integer ventaId){return facturas.findByVentaId(ventaId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Factura no encontrada"));}
}
