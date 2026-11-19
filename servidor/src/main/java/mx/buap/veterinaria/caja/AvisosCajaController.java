package mx.buap.veterinaria.caja;

import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Recuperación de avisos perdidos mientras Caja estaba desconectada (protegido como /api/ventas/**). */
@RestController
@RequestMapping("/api/ventas/avisos")
public class AvisosCajaController {
    private final AvisosCajaService avisos;
    public AvisosCajaController(AvisosCajaService avisos) { this.avisos = avisos; }

    @GetMapping
    public List<AvisosCajaService.Aviso> listar(@RequestParam(defaultValue = "0") int desde) { return avisos.desde(desde); }
}
