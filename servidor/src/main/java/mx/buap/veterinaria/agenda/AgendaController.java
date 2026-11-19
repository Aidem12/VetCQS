package mx.buap.veterinaria.agenda;

import java.time.*;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import mx.buap.veterinaria.comun.SesionActual;
import mx.buap.veterinaria.model.Usuario;
import mx.buap.veterinaria.repository.UsuarioRepository;

/** API de la agenda. Los permisos por rol se aplican en SecurityConfig (/api/agenda/**). */
@RestController
@RequestMapping("/api/agenda")
@org.springframework.transaction.annotation.Transactional // los DTO se arman con la sesión JPA abierta
public class AgendaController {
    private final AgendaService agenda;
    private final TipoCitaRepository tipos;
    private final HorarioVeterinarioRepository horarios;
    private final AusenciaVeterinarioRepository ausencias;
    private final UsuarioRepository usuarios;
    private final SesionActual sesion;

    public AgendaController(AgendaService agenda, TipoCitaRepository tipos, HorarioVeterinarioRepository horarios,
            AusenciaVeterinarioRepository ausencias, UsuarioRepository usuarios, SesionActual sesion) {
        this.agenda = agenda; this.tipos = tipos; this.horarios = horarios; this.ausencias = ausencias;
        this.usuarios = usuarios; this.sesion = sesion;
    }

    // ---------------- DTO de entrada y salida (sin exponer entidades JPA)
    public record CitaRequest(@NotNull Integer mascotaId, @NotNull Integer veterinarioId, @NotNull Integer tipoCitaId,
            @NotNull LocalDateTime inicio, @Size(max = 250) String motivo, @Size(max = 4000) String notas) {}
    public record ReprogramarRequest(@NotNull LocalDateTime inicio, Integer veterinarioId) {}
    public record EstadoRequest(@NotNull Cita.Estado estado, @Size(max = 200) String motivo) {}
    public record AtenderRequest(@NotNull Integer consultaId) {}
    public record TipoRequest(@NotBlank @Size(max = 80) String nombre, @Min(5) @Max(480) int duracionMin,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color, Boolean activo) {}
    public record HorarioRequest(@NotNull Integer veterinarioId, @Min(1) @Max(7) short diaSemana,
            @NotNull LocalTime horaInicio, @NotNull LocalTime horaFin) {}
    public record AusenciaRequest(@NotNull Integer veterinarioId, @NotNull AusenciaVeterinario.Tipo tipo,
            @NotNull LocalDateTime inicio, @NotNull LocalDateTime fin, @Size(max = 200) String motivo) {}

    public record CitaDto(Integer id, Integer mascotaId, String mascota, String especie, Integer duenoId, String dueno,
            String telefono, Integer veterinarioId, String veterinario, Integer tipoCitaId, String tipo, String color,
            LocalDateTime inicio, LocalDateTime fin, Cita.Estado estado, String motivo, String notas,
            Integer consultaId, String motivoCancelacion) {
        static CitaDto de(Cita c) {
            var m = c.getMascota(); var d = m.getDueno();
            return new CitaDto(c.getId(), m.getId(), m.getNombre(), m.getEspecie(), d.getId(), d.getNombre(),
                    d.getTelefono(), c.getVeterinario().getId(), c.getVeterinario().getNombre(), c.getTipo().getId(),
                    c.getTipo().getNombre(), c.getTipo().getColor(), c.getInicio(), c.getFin(), c.getEstado(),
                    c.getMotivo(), c.getNotas(), c.getConsulta() == null ? null : c.getConsulta().getId(),
                    c.getMotivoCancelacion());
        }
    }
    public record TipoDto(Integer id, String nombre, int duracionMin, String color, boolean activo) {
        static TipoDto de(TipoCita t) { return new TipoDto(t.getId(), t.getNombre(), t.getDuracionMin(), t.getColor(), t.isActivo()); }
    }
    public record VeterinarioDto(Integer id, String nombre) {}
    public record HorarioDto(Integer id, Integer veterinarioId, short diaSemana, LocalTime horaInicio, LocalTime horaFin) {
        static HorarioDto de(HorarioVeterinario h) {
            return new HorarioDto(h.getId(), h.getVeterinario().getId(), h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin());
        }
    }
    public record AusenciaDto(Integer id, Integer veterinarioId, AusenciaVeterinario.Tipo tipo, LocalDateTime inicio,
            LocalDateTime fin, String motivo) {
        static AusenciaDto de(AusenciaVeterinario a) {
            return new AusenciaDto(a.getId(), a.getVeterinario().getId(), a.getTipo(), a.getInicio(), a.getFin(), a.getMotivo());
        }
    }
    public record HistorialDto(LocalDateTime fecha, String usuario, String accion, String detalle) {}

    // ---------------- citas
    @GetMapping("/citas")
    public List<CitaDto> citas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer veterinarioId, @RequestParam(required = false) Integer mascotaId,
            @RequestParam(required = false) Integer duenoId, @RequestParam(required = false) Cita.Estado estado,
            @RequestParam(defaultValue = "") String q) {
        return agenda.buscar(desde, hasta, veterinarioId, mascotaId, duenoId, estado, q).stream().map(CitaDto::de).toList();
    }

    @PostMapping("/citas") @ResponseStatus(HttpStatus.CREATED)
    public CitaDto crear(@Valid @RequestBody CitaRequest r, HttpSession session) {
        Cita c = agenda.crear(new AgendaService.NuevaCita(r.mascotaId(), r.veterinarioId(), r.tipoCitaId(), r.inicio(),
                r.motivo(), r.notas()), sesion.usuario(session));
        return CitaDto.de(c);
    }

    @PutMapping("/citas/{id}/reprogramar")
    public CitaDto reprogramar(@PathVariable Integer id, @Valid @RequestBody ReprogramarRequest r, HttpSession session) {
        return CitaDto.de(agenda.reprogramar(id, r.inicio(), r.veterinarioId(), sesion.usuario(session)));
    }

    @PutMapping("/citas/{id}/estado")
    public CitaDto estado(@PathVariable Integer id, @Valid @RequestBody EstadoRequest r, HttpSession session) {
        return CitaDto.de(agenda.cambiarEstado(id, r.estado(), r.motivo(), sesion.usuario(session)));
    }

    @PutMapping("/citas/{id}/atender")
    public CitaDto atender(@PathVariable Integer id, @Valid @RequestBody AtenderRequest r, HttpSession session) {
        return CitaDto.de(agenda.atender(id, r.consultaId(), sesion.usuario(session)));
    }

    @GetMapping("/citas/{id}/historial")
    public List<HistorialDto> historial(@PathVariable Integer id) {
        return agenda.historial(id).stream()
                .map(h -> new HistorialDto(h.getFecha(), h.getUsuario().getNombre(), h.getAccion(), h.getDetalle())).toList();
    }

    @GetMapping("/disponibilidad")
    public List<AgendaService.Espacio> disponibilidad(@RequestParam Integer veterinarioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha, @RequestParam Integer tipoCitaId) {
        return agenda.disponibilidad(veterinarioId, fecha, tipoCitaId);
    }

    /** Recordatorios internos: el veterinario ve los suyos; el administrador, todos. */
    @GetMapping("/recordatorios")
    public List<CitaDto> recordatorios(@RequestParam(defaultValue = "24") int horas, HttpSession session) {
        Usuario u = sesion.usuario(session);
        Integer vet = u.getRol() == Usuario.Rol.VETERINARIO ? u.getId() : null;
        return agenda.recordatorios(horas, vet).stream().map(CitaDto::de).toList();
    }

    // ---------------- catálogos y configuración
    @GetMapping("/veterinarios")
    public List<VeterinarioDto> veterinarios() {
        return usuarios.findAll().stream().filter(Usuario::isActivo).filter(u -> u.getRol() == Usuario.Rol.VETERINARIO)
                .map(u -> new VeterinarioDto(u.getId(), u.getNombre()))
                .sorted(java.util.Comparator.comparing(VeterinarioDto::nombre)).toList();
    }

    @GetMapping("/tipos")
    public List<TipoDto> tipos(@RequestParam(defaultValue = "false") boolean todos) {
        return (todos ? tipos.findAllByOrderByNombre() : tipos.findByActivoTrueOrderByNombre()).stream().map(TipoDto::de).toList();
    }

    @PostMapping("/tipos") @ResponseStatus(HttpStatus.CREATED)
    public TipoDto crearTipo(@Valid @RequestBody TipoRequest r, HttpSession session) {
        soloAdministrador(session);
        TipoCita t = new TipoCita();
        copiar(r, t);
        return TipoDto.de(tipos.save(t));
    }

    @PutMapping("/tipos/{id}")
    public TipoDto editarTipo(@PathVariable Integer id, @Valid @RequestBody TipoRequest r, HttpSession session) {
        soloAdministrador(session);
        TipoCita t = tipos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo no encontrado."));
        copiar(r, t);
        return TipoDto.de(tipos.save(t));
    }

    @GetMapping("/horarios")
    public List<HorarioDto> horarios(@RequestParam Integer veterinarioId) {
        return horarios.findByVeterinarioIdOrderByDiaSemanaAscHoraInicioAsc(veterinarioId).stream().map(HorarioDto::de).toList();
    }

    @PostMapping("/horarios") @ResponseStatus(HttpStatus.CREATED)
    public HorarioDto agregarHorario(@Valid @RequestBody HorarioRequest r, HttpSession session) {
        soloAdministrador(session);
        return HorarioDto.de(agenda.agregarHorario(r.veterinarioId(), r.diaSemana(), r.horaInicio(), r.horaFin()));
    }

    @DeleteMapping("/horarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarHorario(@PathVariable Integer id, HttpSession session) {
        soloAdministrador(session);
        horarios.deleteById(id);
    }

    @GetMapping("/ausencias")
    public List<AusenciaDto> ausencias(@RequestParam Integer veterinarioId) {
        return ausencias.findByVeterinarioIdAndFinAfterOrderByInicio(veterinarioId, LocalDateTime.now().minusDays(30))
                .stream().map(AusenciaDto::de).toList();
    }

    /** El administrador registra ausencias de cualquiera; un veterinario solo las propias. */
    @PostMapping("/ausencias") @ResponseStatus(HttpStatus.CREATED)
    public AusenciaDto agregarAusencia(@Valid @RequestBody AusenciaRequest r, HttpSession session) {
        Usuario u = sesion.usuario(session);
        if (!SesionActual.esAdministrador(u) && !u.getId().equals(r.veterinarioId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede registrar sus propias ausencias.");
        return AusenciaDto.de(agenda.agregarAusencia(r.veterinarioId(), r.tipo(), r.inicio(), r.fin(), r.motivo(), u));
    }

    private void soloAdministrador(HttpSession session) {
        if (!SesionActual.esAdministrador(sesion.usuario(session)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el administrador configura la agenda.");
    }
    private static void copiar(TipoRequest r, TipoCita t) {
        t.setNombre(r.nombre().trim());
        t.setDuracionMin(r.duracionMin());
        if (r.color() != null) t.setColor(r.color());
        if (r.activo() != null) t.setActivo(r.activo());
    }
}
