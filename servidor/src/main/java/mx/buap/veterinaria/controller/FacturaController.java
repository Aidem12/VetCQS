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
 private mx.buap.veterinaria.comun.AuditoriaService auditoria;
 /** Timbrado: por defecto el de demostración (sin PAC). */
 private mx.buap.veterinaria.facturacion.ServicioTimbrado timbrado=new mx.buap.veterinaria.facturacion.TimbradoDemostracion();
 @org.springframework.beans.factory.annotation.Autowired(required=false) public void setTimbrado(mx.buap.veterinaria.facturacion.ServicioTimbrado t){timbrado=t;}
 public FacturaController(FacturaRepository f, VentaRepository v){facturas=f;ventas=v;}
 @org.springframework.beans.factory.annotation.Autowired(required=false) public void setAuditoria(mx.buap.veterinaria.comun.AuditoriaService a){auditoria=a;}
 public Factura crear(Integer ventaId, FacturaRequest r){return crear(ventaId,r,null);}
 @org.springframework.transaction.annotation.Transactional
 @PostMapping("/venta/{ventaId}") @ResponseStatus(HttpStatus.CREATED)
 public Factura crear(@PathVariable Integer ventaId,@Valid @RequestBody FacturaRequest r,jakarta.servlet.http.HttpSession session){
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
   var resultado=timbrado.timbrar(f); f.setIntentos(f.getIntentos()+1);
   f.setEstado(resultado.estado()); f.setUuidSat(resultado.uuid()); f.setXml(resultado.xml());
   f.setRespuestaMock(resultado.respuesta()); f.setMock(resultado.simulado());
   Factura guardada=facturas.save(f);
   if(auditoria!=null && session!=null && session.getAttribute("usuarioId") instanceof Integer u)
    auditoria.registrar(u,"FACTURA_DEMO","factura",guardada.getId(),"Venta "+v.getFolio()+" | RFC "+guardada.getRfcReceptor()+" | simulada, sin validez fiscal");
   return guardada;
 }
 @GetMapping("/venta/{ventaId}") public Factura consultar(@PathVariable Integer ventaId){return facturas.findByVentaId(ventaId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Factura no encontrada"));}
}
