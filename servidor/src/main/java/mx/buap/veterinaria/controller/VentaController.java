package mx.buap.veterinaria.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import mx.buap.veterinaria.dto.PagoRequest;
import mx.buap.veterinaria.dto.VentaRequest;
import mx.buap.veterinaria.dto.PrecuentaRequest;
import mx.buap.veterinaria.model.Venta;
import mx.buap.veterinaria.service.VentaService;

@RestController
@RequestMapping("/api/ventas")
@org.springframework.transaction.annotation.Transactional
public class VentaController {

    private final mx.buap.veterinaria.service.VentaAutorizacionService autorizacion;
    private final VentaService servicio;
    private final mx.buap.veterinaria.repository.VentaRepository ventas;

    public VentaController(VentaService servicio, mx.buap.veterinaria.repository.VentaRepository ventas, mx.buap.veterinaria.service.VentaAutorizacionService autorizacion) {
        this.autorizacion=autorizacion;
        this.ventas=ventas;
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Object crear(@Valid @RequestBody VentaRequest request) {
        return vista(servicio.crear(request));
    }

    @PostMapping("/precuentas")
    @ResponseStatus(HttpStatus.CREATED)
    public Object crearPrecuenta(@Valid @RequestBody PrecuentaRequest request) {
        return vista(servicio.crearPrecuenta(request));
    }

    @PostMapping("/{id}/cobro")
    public Object cobrar(@PathVariable Integer id, @NotEmpty @Valid @RequestBody List<@Valid PagoRequest> pagos, jakarta.servlet.http.HttpSession session) {
        return vista(servicio.cobrar(id, pagos, (Integer)session.getAttribute("usuarioId")));
    }

    @org.springframework.web.bind.annotation.GetMapping("/pendientes")
    public Object pendientes() {
        return ventas.findTop100ByEstadoInOrderByFechaAsc(java.util.List.of(Venta.Estado.PRECUENTA,Venta.Estado.ABIERTA)).stream().map(this::vista).toList();
    }
    @org.springframework.web.bind.annotation.GetMapping("/{id}")
    public Object consultar(@PathVariable Integer id) {return vista(ventas.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND)));}
    @PostMapping("/{id}/autorizar")
    public Object autorizar(@PathVariable Integer id,@Valid @RequestBody mx.buap.veterinaria.dto.AutorizacionRequest r,jakarta.servlet.http.HttpSession session) {
        return vista(autorizacion.aplicar(id,(Integer)session.getAttribute("usuarioId"),r));
    }
    private Object vista(Venta v) {
        java.util.Map<String,Object> m=new java.util.LinkedHashMap<>();
        m.put("id",v.getId());m.put("folio",v.getFolio());m.put("estado",v.getEstado().name());
        m.put("subtotal",v.getSubtotal());m.put("descuento",v.getDescuento());m.put("total",v.getTotal());
        m.put("detalles",v.getDetalles().stream().map(d -> java.util.Map.of("id",d.getId(),"producto",d.getProducto(),"cantidad",d.getCantidad(),"precioUnitario",d.getPrecioUnitario())).toList());
        if(v.getConsulta()!=null){m.put("paciente",v.getConsulta().getMascota().getNombre());m.put("dueno",v.getConsulta().getMascota().getDueno().getNombre());m.put("indicaciones",java.util.Objects.toString(v.getConsulta().getTratamiento(),""));}
        return m;
    }
}
