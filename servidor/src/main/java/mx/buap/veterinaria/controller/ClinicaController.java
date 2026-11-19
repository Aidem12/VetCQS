package mx.buap.veterinaria.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import mx.buap.veterinaria.dto.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clinica")
public class ClinicaController {
    private final DuenoRepository duenos;
    private final MascotaRepository mascotas;
    private final ConsultaRepository consultas;
    private final UsuarioRepository usuarios;
    private final mx.buap.veterinaria.comun.AuditoriaService auditoria;

    public ClinicaController(DuenoRepository duenos, MascotaRepository mascotas,
            ConsultaRepository consultas, UsuarioRepository usuarios, mx.buap.veterinaria.comun.AuditoriaService auditoria) {
        this.auditoria = auditoria;
        this.duenos = duenos;
        this.mascotas = mascotas;
        this.consultas = consultas;
        this.usuarios = usuarios;
    }

    @GetMapping("/duenos")
    public List<Dueno> buscarDuenos(@RequestParam(defaultValue = "") String q) {
        return duenos.findTop20ByNombreContainingIgnoreCaseOrTelefonoContainingOrderByNombre(q, q);
    }

    @PostMapping("/duenos")
    @ResponseStatus(HttpStatus.CREATED)
    public Dueno crearDueno(@Valid @RequestBody DuenoRequest request) {
        Dueno dueno = new Dueno();
        dueno.setNombre(request.nombre());
        dueno.setTelefono(request.telefono());
        dueno.setCorreo(request.correo());
        dueno.setDireccion(request.direccion());
        dueno.setTelefonoAlterno(request.telefonoAlterno());
        dueno.setNotas(request.notas());
        return duenos.save(dueno);
    }

    /** Edición de datos del propietario; el cambio queda en la bitácora. */
    @PutMapping("/duenos/{duenoId}")
    public Dueno editarDueno(@PathVariable Integer duenoId, @Valid @RequestBody DuenoRequest request,
            jakarta.servlet.http.HttpSession session) {
        Dueno dueno = duenos.findById(duenoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dueño no encontrado"));
        String antes = dueno.getNombre() + " / " + dueno.getTelefono();
        dueno.setNombre(request.nombre()); dueno.setTelefono(request.telefono()); dueno.setCorreo(request.correo());
        dueno.setDireccion(request.direccion()); dueno.setTelefonoAlterno(request.telefonoAlterno()); dueno.setNotas(request.notas());
        Dueno guardado = duenos.save(dueno);
        auditoria.registrar(usuarioId(session), "DUENO_EDICION", "duenio", duenoId, "Antes: " + antes);
        return guardado;
    }

    @GetMapping("/duenos/{duenoId}/mascotas")
    public List<MascotaResumen> mascotas(@PathVariable Integer duenoId) {
        return mascotas.findByDuenoIdOrderByNombre(duenoId).stream()
                .map(ClinicaController::resumen).toList();
    }

    @PostMapping("/duenos/{duenoId}/mascotas")
    @ResponseStatus(HttpStatus.CREATED)
    public MascotaResumen crearMascota(@PathVariable Integer duenoId,
            @Valid @RequestBody MascotaRequest request) {
        Mascota mascota = new Mascota();
        mascota.setDueno(duenos.findById(duenoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Dueño no encontrado")));
        copiar(request, mascota);
        return resumen(mascotas.save(mascota));
    }

    /** Edición de datos de la mascota; se conserva el valor anterior en la bitácora. */
    @PutMapping("/mascotas/{mascotaId}")
    public MascotaResumen editarMascota(@PathVariable Integer mascotaId, @Valid @RequestBody MascotaRequest request,
            jakarta.servlet.http.HttpSession session) {
        Mascota mascota = mascotas.findById(mascotaId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Mascota no encontrada"));
        String antes = mascota.getNombre() + " / " + mascota.getEspecie() + " / " + mascota.getPesoKg() + " kg";
        copiar(request, mascota);
        MascotaResumen r = resumen(mascotas.save(mascota));
        auditoria.registrar(usuarioId(session), "MASCOTA_EDICION", "mascota", mascotaId, "Antes: " + antes);
        return r;
    }

    @GetMapping("/mascotas/{mascotaId}/consultas")
    public List<ConsultaResumen> historial(@PathVariable Integer mascotaId) {
        return consultas.findByMascotaIdOrderByFechaDesc(mascotaId).stream()
                .map(ClinicaController::resumen).toList();
    }

    @PostMapping("/consultas")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultaResumen crearConsulta(@Valid @RequestBody ConsultaRequest request, jakarta.servlet.http.HttpSession session) {
        Object usuarioId = session.getAttribute("usuarioId");
        if (!(usuarioId instanceof Integer id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Consulta consulta = new Consulta();
        consulta.setMascota(mascotas.findById(request.mascotaId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Mascota no encontrada")));
        consulta.setVeterinario(usuarios.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Veterinario no encontrado")));
        consulta.setMotivo(request.motivo());
        consulta.setPesoKg(request.pesoKg());
        consulta.setTemperaturaC(request.temperaturaC());
        consulta.setDiagnostico(request.diagnostico());
        consulta.setTratamiento(request.tratamiento());
        consulta.setFrecuenciaCardiaca(request.frecuenciaCardiaca());
        consulta.setFrecuenciaRespiratoria(request.frecuenciaRespiratoria());
        consulta.setCondicionCorporal(request.condicionCorporal() == null ? null : request.condicionCorporal().shortValue());
        consulta.setEvolucion(request.evolucion());
        ConsultaResumen guardada = resumen(consultas.save(consulta));
        auditoria.registrar(id, "CONSULTA_ALTA", "consulta", guardada.id(), "Mascota " + request.mascotaId());
        return guardada;
    }

    private static void copiar(MascotaRequest request, Mascota mascota) {
        mascota.setNombre(request.nombre());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza());
        mascota.setSexo(request.sexo());
        mascota.setFechaNacimiento(request.fechaNacimiento());
        mascota.setPesoKg(request.pesoKg());
        mascota.setIdentificacion(request.identificacion());
        mascota.setColor(request.color());
        mascota.setEsterilizado(request.esterilizado());
        mascota.setObservaciones(request.observaciones());
    }
    private static Integer usuarioId(jakarta.servlet.http.HttpSession session) {
        Object id = session.getAttribute("usuarioId");
        if (!(id instanceof Integer valor)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return valor;
    }
    private static MascotaResumen resumen(Mascota m) {
        return new MascotaResumen(m.getId(), m.getNombre(), m.getEspecie(), m.getRaza(),
                m.getSexo(), m.getPesoKg(), m.getFechaNacimiento(), m.getIdentificacion(), m.getColor(),
                m.getEsterilizado(), m.getObservaciones());
    }
    private static ConsultaResumen resumen(Consulta c) {
        return new ConsultaResumen(c.getId(), c.getFecha(), c.getMotivo(), c.getPesoKg(),
                c.getTemperaturaC(), c.getDiagnostico(), c.getTratamiento());
    }
    public record MascotaResumen(Integer id, String nombre, String especie, String raza,
            String sexo, java.math.BigDecimal pesoKg, java.time.LocalDate fechaNacimiento, String identificacion,
            String color, Boolean esterilizado, String observaciones) {}
    public record ConsultaResumen(Integer id, java.time.LocalDateTime fecha, String motivo,
            java.math.BigDecimal pesoKg, java.math.BigDecimal temperaturaC,
            String diagnostico, String tratamiento) {}
}
