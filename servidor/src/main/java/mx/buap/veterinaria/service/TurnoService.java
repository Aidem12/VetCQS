package mx.buap.veterinaria.service;
import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.dto.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;
@Service @Transactional
public class TurnoService {
 private final TurnoRepository turnos; private final UsuarioRepository usuarios; private final PagoConsultaRepository pagos;
 private final SalidaEfectivoRepository salidas; private final AutorizacionService auth; private final BitacoraRepository bitacora;
 private final VentaRepository ventas; private final ObjectMapper json;
 public TurnoService(TurnoRepository t,UsuarioRepository u,PagoConsultaRepository p,SalidaEfectivoRepository s,
   AutorizacionService a,BitacoraRepository b,VentaRepository v,ObjectMapper j){turnos=t;usuarios=u;pagos=p;salidas=s;auth=a;bitacora=b;ventas=v;json=j;}
 public Map<String,Object> abrir(TurnoAperturaRequest r,Integer operador){
  if(turnos.findByCierreIsNull().isPresent())throw fallo("Ya existe un turno abierto");
  Usuario u=usuarios.findById(operador).filter(Usuario::isActivo).filter(x -> x.getRol()==Usuario.Rol.CAJERO).orElseThrow(() -> fallo("Cajero no válido"));
  Turno t=new Turno();t.setCajero(u);t.setFondoInicial(r.fondoInicial());turnos.save(t);
  auth.registrar(operador,null,"APERTURA","turno",t.getId(),"Fondo: "+r.fondoInicial());return resumen(t);
 }
 public Turno propioAbierto(Integer operador){
  Turno t=turnos.findOpenForUpdate().orElseThrow(() -> fallo("Abra un turno antes de continuar"));
  if(!t.getCajero().getId().equals(operador))throw fallo("El turno pertenece a otro cajero");return t;
 }
 @Transactional(readOnly=true) public Map<String,Object> abierto(){return turnos.findByCierreIsNull().map(this::resumen).orElse(Map.of());}
 private Map<String,Object> resumen(Turno t){return Map.of("id",t.getId(),"cajeroId",t.getCajero().getId(),"cajero",t.getCajero().getNombre(),"apertura",t.getApertura(),"fondoInicial",t.getFondoInicial());}
 public SalidaEfectivo salida(SalidaRequest r,Integer operador){
  Turno t=propioAbierto(operador);Usuario admin=auth.validar(r.usuarioAdmin(),r.claveAdmin());
  if(r.monto().compareTo(esperado(t))>0)throw fallo("El retiro supera el efectivo disponible");
  SalidaEfectivo salida=salidas.save(new SalidaEfectivo(t.getId(),r.concepto(),r.monto(),admin.getId()));
  auth.registrar(operador,admin.getId(),"SALIDA_EFECTIVO","salida_efectivo",salida.getId(),r.concepto()+" | Monto: "+r.monto());return salida;
 }
 public List<SalidaEfectivo> salidas(Integer operador){return salidas.findByTurnoIdOrderByFecha(propioAbierto(operador).getId());}
 private BigDecimal retiros(Turno t){return salidas.findByTurnoIdOrderByFecha(t.getId()).stream().map(SalidaEfectivo::getMonto).reduce(BigDecimal.ZERO,BigDecimal::add);}
 private BigDecimal total(Turno t,Pago.Forma forma){return pagos.totalDelTurno(t.getId(),forma);}
 private BigDecimal esperado(Turno t){return t.getFondoInicial().add(total(t,Pago.Forma.EFECTIVO)).subtract(retiros(t));}
 public Map<String,Object> cerrar(TurnoCierreRequest r,Integer operador){
  Turno t=propioAbierto(operador);
  if(r.denominaciones()!=null){
   Set<String> permitidas=Set.of("1000","500","200","100","50","20","10","5","2","1","0.50");
   BigDecimal contado=BigDecimal.ZERO;
   for(var e:r.denominaciones().entrySet()){
    if(!permitidas.contains(e.getKey()) || e.getValue()==null || e.getValue()<0)throw fallo("Conteo por denominación inválido");
    contado=contado.add(new BigDecimal(e.getKey()).multiply(BigDecimal.valueOf(e.getValue())));
   }
   if(contado.compareTo(r.efectivoContado())!=0)throw fallo("El conteo no coincide con las denominaciones");
  }
  BigDecimal esperado=esperado(t);t.cerrar(r.efectivoContado(),esperado);turnos.save(t);
  Map<String,Object> reporte=new LinkedHashMap<>(resumen(t));reporte.put("cierre",t.getCierre().toString());
  reporte.put("efectivoEsperado",esperado);reporte.put("efectivoContado",r.efectivoContado());reporte.put("diferencia",r.efectivoContado().subtract(esperado));
  reporte.put("ventasEfectivo",total(t,Pago.Forma.EFECTIVO));reporte.put("ventasTarjeta",total(t,Pago.Forma.TARJETA));reporte.put("ventasTransferencia",total(t,Pago.Forma.TRANSFERENCIA));
  reporte.put("salidas",retiros(t));reporte.put("denominaciones",r.denominaciones()==null?Map.of():r.denominaciones());
  reporte.put("tarjetaContada",r.tarjetaContada()==null?BigDecimal.ZERO:r.tarjetaContada());
  reporte.put("transferenciaContada",r.transferenciaContada()==null?BigDecimal.ZERO:r.transferenciaContada());
  reporte.put("transacciones",ventas.findByTurnoIdOrderByFecha(t.getId()).stream().map(v -> Map.of("folio",v.getFolio(),"estado",v.getEstado().name(),"total",v.getTotal())).toList());
  try{auth.registrar(operador,null,"CORTE","turno",t.getId(),json.writeValueAsString(reporte));}catch(Exception e){throw new IllegalStateException("No se pudo registrar el corte",e);}
  return reporte;
 }
 @Transactional(readOnly=true) public Map<String,Object> reporte(Integer id,Integer operador){
  Turno t=turnos.findById(id).filter(x -> x.getCajero().getId().equals(operador) && x.getCierre()!=null).orElseThrow(() -> fallo("Corte no disponible"));
  try{return json.readValue(bitacora.findTopByTablaAndRegistroIdAndAccionOrderByIdDesc("turno",id,"CORTE").orElseThrow().getDetalle(),new TypeReference<Map<String,Object>>(){});}
  catch(Exception e){return Map.of("id",t.getId(),"efectivoEsperado",t.getEfectivoEsperado(),"efectivoContado",t.getEfectivoContado(),"diferencia",t.getEfectivoContado().subtract(t.getEfectivoEsperado()),"cierre",t.getCierre());}
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> historial(Integer operador){return turnos.findTop50ByCajeroIdAndCierreIsNotNullOrderByCierreDesc(operador).stream().map(t -> {Map<String,Object> m=new LinkedHashMap<>(resumen(t));m.put("cierre",t.getCierre());return m;}).toList();}
 private ResponseStatusException fallo(String mensaje){return new ResponseStatusException(HttpStatus.CONFLICT,mensaje);}
}
