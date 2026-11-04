package mx.buap.veterinaria.controller;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import mx.buap.veterinaria.dto.*;
import mx.buap.veterinaria.service.TurnoService;
@RestController @RequestMapping("/api/turnos")
public class TurnoController {
 private final TurnoService servicio;public TurnoController(TurnoService s){servicio=s;}
 @GetMapping("/abierto") public Object abierto(){return servicio.abierto();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Object abrir(@Valid @RequestBody TurnoAperturaRequest r,HttpSession s){return servicio.abrir(r,(Integer)s.getAttribute("usuarioId"));}
 @PostMapping("/cierre") public Object cerrar(@Valid @RequestBody TurnoCierreRequest r,HttpSession s){return servicio.cerrar(r,(Integer)s.getAttribute("usuarioId"));}
 @PostMapping("/salidas") @ResponseStatus(HttpStatus.CREATED) public Object salida(@Valid @RequestBody SalidaRequest r,HttpSession s){return servicio.salida(r,(Integer)s.getAttribute("usuarioId"));}
 @GetMapping("/salidas") public Object salidas(HttpSession s){return servicio.salidas((Integer)s.getAttribute("usuarioId"));}
 @GetMapping("/historial") public Object historial(HttpSession s){return servicio.historial((Integer)s.getAttribute("usuarioId"));}
 @GetMapping("/{id}/reporte") public Object reporte(@PathVariable Integer id,HttpSession s){return servicio.reporte(id,(Integer)s.getAttribute("usuarioId"));}
}
