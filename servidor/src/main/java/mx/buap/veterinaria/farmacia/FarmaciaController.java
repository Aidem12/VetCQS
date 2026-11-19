package mx.buap.veterinaria.farmacia;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import mx.buap.veterinaria.comun.*;

/** Farmacia: solo ADMINISTRADOR, salvo la consulta de lotes vigentes (SecurityConfig). */
@RestController
@RequestMapping("/api/farmacia")
@Transactional
public class FarmaciaController {
    private final FarmaciaService farmacia;
    private final ProveedorRepository proveedores;
    private final CompraRepository compras;
    private final SesionActual sesion;
    private final Reloj reloj;

    public FarmaciaController(FarmaciaService farmacia, ProveedorRepository proveedores, CompraRepository compras,
            SesionActual sesion, Reloj reloj) {
        this.farmacia = farmacia; this.proveedores = proveedores; this.compras = compras; this.sesion = sesion; this.reloj = reloj;
    }

    public record ProveedorRequest(@NotBlank @Size(max = 150) String nombre, @Size(max = 13) String rfc,
            @Size(max = 120) String contacto, @Size(max = 20) String telefono, @Size(max = 120) @Email String correo,
            @Size(max = 200) String direccion, @Size(max = 500) String notas) {}
    public record ActivoRequest(boolean activo) {}
    public record CompraRequest(@NotNull Integer proveedorId, @Size(max = 60) String documento, @Size(max = 300) String notas,
            @NotEmpty @Size(max = 100) List<@Valid RenglonRequest> productos) {}
    public record RenglonRequest(@NotNull Integer productoId, @Size(max = 40) String numeroLote, LocalDate caducidad,
            @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal cantidad,
            @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal costoUnitario) {}
    public record AjusteLoteRequest(@NotNull @Digits(integer = 8, fraction = 2) BigDecimal cantidad,
            @NotBlank @Size(max = 200) String motivo) {}

    public record ProveedorDto(Integer id, String nombre, String rfc, String contacto, String telefono, String correo,
            String direccion, String notas, boolean activo) {
        static ProveedorDto de(Proveedor p) {
            return new ProveedorDto(p.getId(), p.getNombre(), p.getRfc(), p.getContacto(), p.getTelefono(), p.getCorreo(),
                    p.getDireccion(), p.getNotas(), p.isActivo());
        }
    }
    public record CompraDto(Integer id, LocalDateTime fecha, String proveedor, String documento, BigDecimal total,
            String usuario, int renglones) {}
    public record LoteDto(Integer id, Integer productoId, String producto, String numeroLote, LocalDate caducidad,
            BigDecimal cantidad, BigDecimal cantidadRecibida, BigDecimal costoUnitario, String proveedor,
            LocalDateTime fechaRecepcion, boolean vencido, long diasRestantes) {}

    // ---------------- proveedores
    @GetMapping("/proveedores")
    public List<ProveedorDto> proveedores() { return proveedores.findAllByOrderByNombre().stream().map(ProveedorDto::de).toList(); }

    @PostMapping("/proveedores") @ResponseStatus(HttpStatus.CREATED)
    public ProveedorDto crearProveedor(@Valid @RequestBody ProveedorRequest r, HttpSession s) {
        return ProveedorDto.de(farmacia.guardarProveedor(null, datos(r), sesion.usuario(s)));
    }
    @PutMapping("/proveedores/{id}")
    public ProveedorDto editarProveedor(@PathVariable Integer id, @Valid @RequestBody ProveedorRequest r, HttpSession s) {
        return ProveedorDto.de(farmacia.guardarProveedor(id, datos(r), sesion.usuario(s)));
    }
    @PutMapping("/proveedores/{id}/activo")
    public ProveedorDto activo(@PathVariable Integer id, @RequestBody ActivoRequest r, HttpSession s) {
        return ProveedorDto.de(farmacia.activarProveedor(id, r.activo(), sesion.usuario(s)));
    }

    // ---------------- compras y lotes
    @GetMapping("/compras")
    public List<CompraDto> compras(@RequestParam(defaultValue = "50") int limite) {
        return compras.recientes(org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limite, 200)))).stream()
                .map(c -> new CompraDto(c.getId(), c.getFecha(), c.getProveedor().getNombre(), c.getDocumento(), c.getTotal(),
                        c.getUsuario().getNombre(), c.getDetalles().size())).toList();
    }
    @PostMapping("/compras") @ResponseStatus(HttpStatus.CREATED)
    public CompraDto registrarCompra(@Valid @RequestBody CompraRequest r, HttpSession s) {
        Compra c = farmacia.registrarCompra(r.proveedorId(), r.documento(), r.notas(), r.productos().stream()
                .map(x -> new FarmaciaService.RenglonCompra(x.productoId(), x.numeroLote(), x.caducidad(), x.cantidad(),
                        x.costoUnitario())).toList(), sesion.usuario(s));
        return new CompraDto(c.getId(), c.getFecha(), c.getProveedor().getNombre(), c.getDocumento(), c.getTotal(),
                c.getUsuario().getNombre(), c.getDetalles().size());
    }

    @GetMapping("/lotes")
    public List<LoteDto> lotes(@RequestParam(required = false) Integer productoId, @RequestParam(required = false) Integer proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate caducaAntes,
            @RequestParam(defaultValue = "true") boolean conExistencia) {
        LocalDate hoy = reloj.hoy();
        return farmacia.lotes(productoId, proveedorId, caducaAntes, conExistencia).stream()
                .map(l -> dto(l, hoy)).toList();
    }
    /** Lotes vigentes de un producto: lo pueden consultar Caja y Consultorio. */
    @GetMapping("/lotes/vigentes")
    public List<LoteDto> vigentes(@RequestParam Integer productoId) {
        LocalDate hoy = reloj.hoy();
        return farmacia.lotes(productoId, null, null, true).stream().filter(l -> !l.vencido(hoy)).map(l -> dto(l, hoy)).toList();
    }
    @PutMapping("/lotes/{id}/ajuste")
    public LoteDto ajustar(@PathVariable Integer id, @Valid @RequestBody AjusteLoteRequest r, HttpSession s) {
        return dto(farmacia.ajustarLote(id, r.cantidad(), r.motivo(), sesion.usuario(s)), reloj.hoy());
    }

    // ---------------- alertas y análisis
    @GetMapping("/caducidades")
    public List<FarmaciaService.AlertaCaducidad> caducidades(@RequestParam(defaultValue = "60") int dias) {
        return farmacia.caducidades(dias);
    }
    @GetMapping("/reposicion")
    public List<FarmaciaService.Reposicion> reposicion() { return farmacia.reposicion(); }
    @GetMapping("/margenes")
    public List<FarmaciaService.Margen> margenes() { return farmacia.margenes(); }

    private static FarmaciaService.ProveedorDatos datos(ProveedorRequest r) {
        return new FarmaciaService.ProveedorDatos(r.nombre(), r.rfc(), r.contacto(), r.telefono(), r.correo(), r.direccion(), r.notas());
    }
    private static LoteDto dto(Lote l, LocalDate hoy) {
        return new LoteDto(l.getId(), l.getProducto().getId(), l.getProducto().getNombre(), l.getNumeroLote(), l.getCaducidad(),
                l.getCantidad(), l.getCantidadRecibida(), l.getCostoUnitario(), l.getProveedor() == null ? null : l.getProveedor().getNombre(),
                l.getFechaRecepcion(), l.vencido(hoy), java.time.temporal.ChronoUnit.DAYS.between(hoy, l.getCaducidad()));
    }
}
