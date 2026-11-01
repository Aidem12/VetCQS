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

    public ClinicaController(DuenoRepository duenos, MascotaRepository mascotas,
            ConsultaRepository consultas, UsuarioRepository usuarios) {
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
        return duenos.save(dueno);
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

    @GetMapping("/mascotas/{mascotaId}/consultas")
    public List<ConsultaResumen> historial(@PathVariable Integer mascotaId) {
        return consultas.findByMascotaIdOrderByFechaDesc(mascotaId).stream()
                .map(ClinicaController::resumen).toList();
    }

    @PostMapping("/consultas")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultaResumen crearConsulta(@Valid @RequestBody ConsultaRequest request) {
        Consulta consulta = new Consulta();
        consulta.setMascota(mascotas.findById(request.mascotaId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Mascota no encontrada")));
        consulta.setVeterinario(usuarios.findById(request.veterinarioId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Veterinario no encontrado")));
        consulta.setMotivo(request.motivo());
        consulta.setPesoKg(request.pesoKg());
        consulta.setTemperaturaC(request.temperaturaC());
        consulta.setDiagnostico(request.diagnostico());
        consulta.setTratamiento(request.tratamiento());
        return resumen(consultas.save(consulta));
    }

    private static void copiar(MascotaRequest request, Mascota mascota) {
        mascota.setNombre(request.nombre());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza());
        mascota.setSexo(request.sexo());
        mascota.setFechaNacimiento(request.fechaNacimiento());
        mascota.setPesoKg(request.pesoKg());
    }
    private static MascotaResumen resumen(Mascota m) {
        return new MascotaResumen(m.getId(), m.getNombre(), m.getEspecie(), m.getRaza(),
                m.getSexo(), m.getPesoKg());
    }
    private static ConsultaResumen resumen(Consulta c) {
        return new ConsultaResumen(c.getId(), c.getFecha(), c.getMotivo(), c.getPesoKg(),
                c.getTemperaturaC(), c.getDiagnostico(), c.getTratamiento());
    }
    public record MascotaResumen(Integer id, String nombre, String especie, String raza,
            String sexo, java.math.BigDecimal pesoKg) {}
    public record ConsultaResumen(Integer id, java.time.LocalDateTime fecha, String motivo,
            java.math.BigDecimal pesoKg, java.math.BigDecimal temperaturaC,
            String diagnostico, String tratamiento) {}
}
