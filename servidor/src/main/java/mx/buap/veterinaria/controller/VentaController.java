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
import mx.buap.veterinaria.model.Venta;
import mx.buap.veterinaria.service.VentaService;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService servicio;

    public VentaController(VentaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Venta crear(@Valid @RequestBody VentaRequest request) {
        return servicio.crear(request);
    }

    @PostMapping("/{id}/cobro")
    public Venta cobrar(@PathVariable Integer id, @NotEmpty @Valid @RequestBody List<@Valid PagoRequest> pagos) {
        return servicio.cobrar(id, pagos);
    }
}
