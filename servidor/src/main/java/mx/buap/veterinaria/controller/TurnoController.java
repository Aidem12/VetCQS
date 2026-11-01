package mx.buap.veterinaria.controller;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import mx.buap.veterinaria.dto.*;
import mx.buap.veterinaria.model.Turno;
import mx.buap.veterinaria.service.TurnoService;

@RestController @RequestMapping("/api/turnos")
public class TurnoController {
    private final TurnoService servicio;
    public TurnoController(TurnoService s) { servicio=s; }
    @GetMapping("/abierto") public Turno abierto() { return servicio.abierto(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Turno abrir(@Valid @RequestBody TurnoAperturaRequest r) { return servicio.abrir(r); }
    @PostMapping("/cierre") public Object cerrar(@Valid @RequestBody TurnoCierreRequest r) { return servicio.cerrar(r); }
}
