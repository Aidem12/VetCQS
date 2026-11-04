package mx.buap.veterinaria.service;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import mx.buap.veterinaria.dto.AutorizacionRequest;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.VentaRepository;
@Service
public class VentaAutorizacionService {
 private final VentaRepository ventas;private final AutorizacionService auth;
 public VentaAutorizacionService(VentaRepository v,AutorizacionService a){ventas=v;auth=a;}
 @Transactional public Venta aplicar(Integer id,Integer operador,AutorizacionRequest r){
  Usuario admin=auth.validar(r.usuarioAdmin(),r.claveAdmin());
  Venta v=ventas.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(v.getEstado()!=Venta.Estado.ABIERTA && v.getEstado()!=Venta.Estado.PRECUENTA)throw fallo("Solo se modifican ventas pendientes de cobro");
  BigDecimal anterior=v.getTotal();
  switch(r.accion()) {
   case "CANCELAR_VENTA" -> v.cancelar(admin,r.motivo());
   case "DESCUENTO" -> {importe(r.importe());if(r.importe().compareTo(v.getSubtotal())>=0)throw fallo("El descuento debe ser menor al subtotal");v.setDescuento(r.importe());v.autorizarDescuento(admin);}
   case "CAMBIAR_PRECIO", "ELIMINAR_PRODUCTO" -> {
    VentaDetalle d=v.getDetalles().stream().filter(x -> x.getId().equals(r.detalleId())).findFirst().orElseThrow(() -> fallo("Seleccione un producto de esta venta"));
    if(r.accion().equals("CAMBIAR_PRECIO")){importe(r.importe());d.setPrecioUnitario(r.importe());}
    else {if(v.getDetalles().size()==1)throw fallo("Para quitar el último producto, cancele la venta");v.getDetalles().remove(d);}
    v.setSubtotal(v.getDetalles().stream().map(x -> x.getCantidad().multiply(x.getPrecioUnitario())).reduce(BigDecimal.ZERO,BigDecimal::add));
    if(v.getDescuento().compareTo(v.getSubtotal())>=0)throw fallo("Revise el descuento antes de modificar el producto");
   }
   default -> throw fallo("Acción no válida");
  }
  v.setTotal(v.getSubtotal().subtract(v.getDescuento()));ventas.save(v);
  auth.registrar(operador,admin.getId(),r.accion(),"venta",id,r.motivo()+" | Total anterior: "+anterior+" | Total actual: "+v.getTotal());return v;
 }
 private void importe(BigDecimal n){if(n==null || n.signum()<0 || n.scale()>2 || n.compareTo(new BigDecimal("99999999.99"))>0)throw fallo("Importe inválido");}
 private ResponseStatusException fallo(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}
}
