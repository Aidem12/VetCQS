package mx.buap.veterinaria.agenda;

import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.comun.Reloj;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

/** Reglas de la agenda: horarios, ausencias, disponibilidad, estados y vínculo con consultas. */
@Service
public class AgendaService {
    /** Transiciones permitidas entre estados. */
    private static final Map<Cita.Estado, Set<Cita.Estado>> TRANSICIONES = Map.of(
            Cita.Estado.PENDIENTE, EnumSet.of(Cita.Estado.CONFIRMADA, Cita.Estado.EN_CURSO, Cita.Estado.CANCELADA, Cita.Estado.NO_ASISTIO),
            Cita.Estado.CONFIRMADA, EnumSet.of(Cita.Estado.EN_CURSO, Cita.Estado.CANCELADA, Cita.Estado.NO_ASISTIO),
            Cita.Estado.EN_CURSO, EnumSet.of(Cita.Estado.ATENDIDA),
            Cita.Estado.ATENDIDA, EnumSet.noneOf(Cita.Estado.class),
            Cita.Estado.CANCELADA, EnumSet.noneOf(Cita.Estado.class),
            Cita.Estado.NO_ASISTIO, EnumSet.noneOf(Cita.Estado.class));
    static final int MAX_DIAS_CONSULTA = 62;

    private final CitaRepository citas;
    private final CitaHistorialRepository historial;
    private final TipoCitaRepository tipos;
    private final HorarioVeterinarioRepository horarios;
    private final AusenciaVeterinarioRepository ausencias;
    private final MascotaRepository mascotas;
    private final UsuarioRepository usuarios;
    private final ConsultaRepository consultas;
    private final Reloj reloj;

    public AgendaService(CitaRepository citas, CitaHistorialRepository historial, TipoCitaRepository tipos,
            HorarioVeterinarioRepository horarios, AusenciaVeterinarioRepository ausencias,
            MascotaRepository mascotas, UsuarioRepository usuarios, ConsultaRepository consultas, Reloj reloj) {
        this.citas = citas; this.historial = historial; this.tipos = tipos; this.horarios = horarios;
        this.ausencias = ausencias; this.mascotas = mascotas; this.usuarios = usuarios; this.consultas = consultas;
        this.reloj = reloj;
    }

    public record NuevaCita(Integer mascotaId, Integer veterinarioId, Integer tipoCitaId, LocalDateTime inicio,
            String motivo, String notas) {}
    public record Espacio(LocalDateTime inicio, LocalDateTime fin) {}

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<Cita> buscar(LocalDate desde, LocalDate hasta, Integer vet, Integer mascota, Integer dueno,
            Cita.Estado estado, String texto) {
        if (hasta.isBefore(desde)) throw error(HttpStatus.BAD_REQUEST, "El rango de fechas es inválido.");
        if (desde.plusDays(MAX_DIAS_CONSULTA).isBefore(hasta))
            throw error(HttpStatus.BAD_REQUEST, "Consulte como máximo " + MAX_DIAS_CONSULTA + " días a la vez.");
        String filtro = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        return citas.buscar(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay(), vet, mascota, dueno, estado, filtro);
    }

    /** Espacios libres de un veterinario en un día para un tipo de cita, en pasos de 15 minutos. */
    @Transactional(readOnly = true)
    public List<Espacio> disponibilidad(Integer vetId, LocalDate fecha, Integer tipoId) {
        Usuario vet = veterinario(vetId);
        TipoCita tipo = tipoActivo(tipoId);
        Duration duracion = Duration.ofMinutes(tipo.getDuracionMin());
        LocalDateTime ahora = reloj.ahora();
        List<Espacio> libres = new ArrayList<>();
        for (HorarioVeterinario bloque : horarios.findByVeterinarioIdAndDiaSemanaOrderByHoraInicio(vet.getId(),
                (short) fecha.getDayOfWeek().getValue())) {
            LocalDateTime cursor = fecha.atTime(bloque.getHoraInicio());
            LocalDateTime finBloque = fecha.atTime(bloque.getHoraFin());
            List<Cita> ocupadas = citas.ocupadas(vet.getId(), cursor, finBloque);
            List<AusenciaVeterinario> bloqueos = ausencias.traslapadas(vet.getId(), cursor, finBloque);
            while (!cursor.plus(duracion).isAfter(finBloque)) {
                LocalDateTime fin = cursor.plus(duracion);
                LocalDateTime inicio = cursor;
                boolean ocupado = ocupadas.stream().anyMatch(c -> c.getInicio().isBefore(fin) && c.getFin().isAfter(inicio))
                        || bloqueos.stream().anyMatch(a -> a.getInicio().isBefore(fin) && a.getFin().isAfter(inicio));
                if (!ocupado && inicio.isAfter(ahora)) libres.add(new Espacio(inicio, fin));
                cursor = cursor.plusMinutes(15);
            }
        }
        return libres;
    }

    @Transactional(readOnly = true)
    public List<CitaHistorial> historial(Integer citaId) { return historial.findByCitaIdOrderByFechaAsc(citaId); }

    /** Recordatorios dentro del sistema: citas pendientes o confirmadas en las próximas horas. */
    @Transactional(readOnly = true)
    public List<Cita> recordatorios(int horas, Integer vet) {
        LocalDateTime ahora = reloj.ahora();
        return citas.proximas(ahora, ahora.plusHours(Math.max(1, Math.min(horas, 168))), vet);
    }

    // ---------------------------------------------------------------- cambios

    @Transactional
    public Cita crear(NuevaCita datos, Usuario autor) {
        Cita cita = new Cita();
        cita.setMascota(mascotas.findById(datos.mascotaId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Mascota no encontrada.")));
        cita.setVeterinario(veterinario(datos.veterinarioId()));
        cita.setTipo(tipoActivo(datos.tipoCitaId()));
        cita.setMotivo(limpiar(datos.motivo(), 250));
        cita.setNotas(limpiar(datos.notas(), 4000));
        cita.setCreadoPor(autor);
        programar(cita, datos.inicio());
        Cita guardada = guardar(cita);
        historial.save(new CitaHistorial(guardada, autor, "CREADA", "Cita para " + guardada.getInicio()));
        return guardada;
    }

    @Transactional
    public Cita reprogramar(Integer citaId, LocalDateTime nuevoInicio, Integer nuevoVet, Usuario autor) {
        Cita cita = bloquear(citaId);
        if (cita.getEstado() != Cita.Estado.PENDIENTE && cita.getEstado() != Cita.Estado.CONFIRMADA)
            throw error(HttpStatus.CONFLICT, "Solo se pueden reprogramar citas pendientes o confirmadas.");
        String antes = cita.getInicio() + " con " + cita.getVeterinario().getNombre();
        if (nuevoVet != null) cita.setVeterinario(veterinario(nuevoVet));
        programar(cita, nuevoInicio);
        cita.setEstado(Cita.Estado.PENDIENTE);
        tocar(cita, autor);
        Cita guardada = guardar(cita);
        historial.save(new CitaHistorial(guardada, autor, "REPROGRAMADA",
                "De " + antes + " a " + guardada.getInicio() + " con " + guardada.getVeterinario().getNombre()));
        return guardada;
    }

    @Transactional
    public Cita cambiarEstado(Integer citaId, Cita.Estado nuevo, String motivo, Usuario autor) {
        Cita cita = bloquear(citaId);
        if (nuevo == Cita.Estado.ATENDIDA)
            throw error(HttpStatus.BAD_REQUEST, "Una cita se marca como atendida al vincular su consulta.");
        validarTransicion(cita.getEstado(), nuevo);
        if (nuevo == Cita.Estado.CANCELADA) {
            if (motivo == null || motivo.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Indique el motivo de la cancelación.");
            cita.setMotivoCancelacion(limpiar(motivo, 200));
        }
        cita.setEstado(nuevo);
        tocar(cita, autor);
        historial.save(new CitaHistorial(cita, autor, nuevo.name(), motivo));
        return cita;
    }

    /** Vincula la consulta registrada y deja la cita como atendida. */
    @Transactional
    public Cita atender(Integer citaId, Integer consultaId, Usuario autor) {
        Cita cita = bloquear(citaId);
        if (cita.getEstado() == Cita.Estado.PENDIENTE || cita.getEstado() == Cita.Estado.CONFIRMADA)
            cita.setEstado(Cita.Estado.EN_CURSO);
        validarTransicion(cita.getEstado(), Cita.Estado.ATENDIDA);
        Consulta consulta = consultas.findById(consultaId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Consulta no encontrada."));
        if (!consulta.getMascota().getId().equals(cita.getMascota().getId()))
            throw error(HttpStatus.CONFLICT, "La consulta pertenece a otra mascota.");
        cita.setConsulta(consulta);
        cita.setEstado(Cita.Estado.ATENDIDA);
        tocar(cita, autor);
        historial.save(new CitaHistorial(cita, autor, "ATENDIDA", "Consulta " + consultaId));
        return cita;
    }

    @Transactional
    public HorarioVeterinario agregarHorario(Integer vetId, short dia, LocalTime inicio, LocalTime fin) {
        if (dia < 1 || dia > 7) throw error(HttpStatus.BAD_REQUEST, "El día debe ir de 1 (lunes) a 7 (domingo).");
        if (inicio == null || fin == null || !fin.isAfter(inicio))
            throw error(HttpStatus.BAD_REQUEST, "La hora final debe ser posterior a la inicial.");
        Usuario vet = veterinario(vetId);
        boolean cruza = horarios.findByVeterinarioIdAndDiaSemanaOrderByHoraInicio(vet.getId(), dia).stream()
                .anyMatch(h -> h.getHoraInicio().isBefore(fin) && h.getHoraFin().isAfter(inicio));
        if (cruza) throw error(HttpStatus.CONFLICT, "Ese bloque se cruza con otro horario del mismo día.");
        HorarioVeterinario h = new HorarioVeterinario();
        h.setVeterinario(vet); h.setDiaSemana(dia); h.setHoraInicio(inicio); h.setHoraFin(fin);
        return horarios.save(h);
    }

    @Transactional
    public AusenciaVeterinario agregarAusencia(Integer vetId, AusenciaVeterinario.Tipo tipo, LocalDateTime inicio,
            LocalDateTime fin, String motivo, Usuario autor) {
        if (tipo == null || inicio == null || fin == null || !fin.isAfter(inicio))
            throw error(HttpStatus.BAD_REQUEST, "Capture el tipo y un periodo válido.");
        AusenciaVeterinario a = new AusenciaVeterinario();
        a.setVeterinario(veterinario(vetId)); a.setTipo(tipo); a.setInicio(inicio); a.setFin(fin);
        a.setMotivo(limpiar(motivo, 200)); a.setCreadoPor(autor);
        return ausencias.save(a);
    }

    // ---------------------------------------------------------------- reglas internas

    /** Calcula el fin y valida horario de atención, ausencias y traslapes. */
    void programar(Cita cita, LocalDateTime inicio) {
        if (inicio == null) throw error(HttpStatus.BAD_REQUEST, "Indique la fecha y hora de la cita.");
        inicio = inicio.withSecond(0).withNano(0);
        if (!inicio.isAfter(reloj.ahora())) throw error(HttpStatus.BAD_REQUEST, "La cita debe ser en una fecha y hora futura.");
        LocalDateTime fin = inicio.plusMinutes(cita.getTipo().getDuracionMin());
        Integer vet = cita.getVeterinario().getId();
        LocalDateTime inicioFinal = inicio;
        boolean dentroHorario = !inicio.toLocalDate().equals(fin.toLocalDate()) ? false
                : horarios.findByVeterinarioIdAndDiaSemanaOrderByHoraInicio(vet, (short) inicio.getDayOfWeek().getValue())
                    .stream().anyMatch(h -> !inicioFinal.toLocalTime().isBefore(h.getHoraInicio())
                            && !fin.toLocalTime().isAfter(h.getHoraFin()));
        if (!dentroHorario) throw error(HttpStatus.CONFLICT, "El horario elegido está fuera del horario de atención del veterinario.");
        if (!ausencias.traslapadas(vet, inicio, fin).isEmpty())
            throw error(HttpStatus.CONFLICT, "El veterinario no está disponible en ese horario (vacaciones o ausencia).");
        if (citas.traslapes(vet, inicio, fin, cita.getId()) > 0)
            throw error(HttpStatus.CONFLICT, "Ese horario ya está ocupado por otra cita del mismo veterinario.");
        cita.setInicio(inicio);
        cita.setFin(fin);
    }

    static void validarTransicion(Cita.Estado actual, Cita.Estado nuevo) {
        if (!TRANSICIONES.getOrDefault(actual, Set.of()).contains(nuevo))
            throw error(HttpStatus.CONFLICT, "No se puede pasar una cita de " + actual + " a " + nuevo + ".");
    }

    private Cita guardar(Cita cita) {
        try {
            return citas.saveAndFlush(cita);
        } catch (DataIntegrityViolationException e) {
            // La restricción de exclusión de PostgreSQL detecta reservas simultáneas.
            throw error(HttpStatus.CONFLICT, "Ese horario acaba de ser ocupado por otra cita. Elija otro.");
        }
    }
    private Cita bloquear(Integer id) {
        return citas.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Cita no encontrada."));
    }
    private void tocar(Cita cita, Usuario autor) {
        cita.setActualizadoPor(autor);
        cita.setActualizadoEn(reloj.ahora());
    }
    private Usuario veterinario(Integer id) {
        if (id == null) throw error(HttpStatus.BAD_REQUEST, "Seleccione un veterinario.");
        return usuarios.findById(id).filter(Usuario::isActivo).filter(u -> u.getRol() == Usuario.Rol.VETERINARIO)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "El veterinario no existe o está inactivo."));
    }
    private TipoCita tipoActivo(Integer id) {
        if (id == null) throw error(HttpStatus.BAD_REQUEST, "Seleccione el tipo de cita.");
        return tipos.findById(id).filter(TipoCita::isActivo)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "El tipo de cita no existe o está inactivo."));
    }
    private static String limpiar(String texto, int max) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
    private static ResponseStatusException error(HttpStatus status, String mensaje) {
        return new ResponseStatusException(status, mensaje);
    }
}
