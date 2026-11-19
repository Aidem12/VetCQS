package mx.buap.veterinaria.expediente;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import mx.buap.veterinaria.comun.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.reportes.Pdf;

/** Expediente clínico. Ruta bajo /api/clinica: solo ADMINISTRADOR y VETERINARIO (SecurityConfig). */
@RestController
@RequestMapping("/api/clinica/expedientes")
@Transactional
public class ExpedienteController {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ExpedienteService servicio;
    private final SesionActual sesion;
    private final Negocio negocio;
    private final Reloj reloj;

    public ExpedienteController(ExpedienteService servicio, SesionActual sesion, Negocio negocio, Reloj reloj) {
        this.servicio = servicio; this.sesion = sesion; this.negocio = negocio; this.reloj = reloj;
    }

    // ---------------------------------------------------------------- DTO
    public record VacunaRequest(@NotBlank @Size(max = 120) String vacuna, @Size(max = 40) String lote,
            @Size(max = 40) String dosis, @NotNull @PastOrPresent LocalDate fechaAplicacion, LocalDate proximoRefuerzo,
            @Size(max = 500) String observaciones) {}
    public record PreventivoRequest(@NotNull TratamientoPreventivo.Tipo tipo, @NotBlank @Size(max = 120) String producto,
            @Size(max = 60) String dosis, @NotNull @PastOrPresent LocalDate fechaAplicacion, LocalDate proximaAplicacion,
            @Size(max = 500) String observaciones) {}
    public record AntecedenteRequest(@NotNull AntecedenteClinico.Tipo tipo, @NotBlank @Size(max = 300) String descripcion,
            AntecedenteClinico.Severidad severidad) {}
    public record AnularRequest(@NotBlank @Size(max = 200) String motivo) {}
    public record NotaRequest(@NotBlank @Size(max = 8000) String texto) {}
    public record RecetaRequest(Integer consultaId, @Size(max = 4000) String indicaciones,
            @NotEmpty @Size(max = 30) List<@Valid RenglonRequest> medicamentos) {}
    public record RenglonRequest(Integer productoId, @Size(max = 160) String medicamento, @NotBlank @Size(max = 80) String dosis,
            @NotBlank @Size(max = 80) String frecuencia, @NotBlank @Size(max = 40) String via,
            @NotBlank @Size(max = 60) String duracion, @Size(max = 400) String indicaciones) {}
    public record EstudioRequest(Integer consultaId, @NotBlank @Size(max = 80) String tipo, @Size(max = 300) String descripcion,
            @Size(max = 20000) String resultado, @NotNull @PastOrPresent LocalDate fecha) {}

    public record MascotaFicha(Integer id, String nombre, String especie, String raza, String sexo, LocalDate fechaNacimiento,
            String edad, java.math.BigDecimal pesoKg, String identificacion, String color, Boolean esterilizado,
            String observaciones, Integer duenoId, String dueno, String telefono, String correo) {}
    public record VacunaDto(Integer id, String vacuna, String lote, String dosis, LocalDate fechaAplicacion,
            LocalDate proximoRefuerzo, String veterinario, String observaciones, boolean anulada, String motivoAnulacion) {}
    public record PreventivoDto(Integer id, TratamientoPreventivo.Tipo tipo, String producto, String dosis,
            LocalDate fechaAplicacion, LocalDate proximaAplicacion, String veterinario, String observaciones,
            boolean anulada, String motivoAnulacion) {}
    public record AntecedenteDto(Integer id, AntecedenteClinico.Tipo tipo, String descripcion,
            AntecedenteClinico.Severidad severidad, boolean vigente, String registradoPor, LocalDateTime creadoEn) {}
    public record ConsultaDto(Integer id, LocalDateTime fecha, String veterinario, String motivo, java.math.BigDecimal pesoKg,
            java.math.BigDecimal temperaturaC, Integer frecuenciaCardiaca, Integer frecuenciaRespiratoria,
            Short condicionCorporal, String diagnostico, String tratamiento, String evolucion, List<NotaDto> notas) {}
    public record NotaDto(Integer id, LocalDateTime fecha, String autor, String texto) {}
    public record RecetaDto(Integer id, String folio, LocalDateTime fecha, String veterinario, Integer consultaId,
            String indicaciones, List<RenglonDto> medicamentos) {}
    public record RenglonDto(Integer productoId, String medicamento, String dosis, String frecuencia, String via,
            String duracion, String indicaciones) {}
    public record EstudioDto(Integer id, LocalDate fecha, String tipo, String descripcion, String resultado,
            String registradoPor, Integer consultaId) {}
    public record FichaDto(MascotaFicha mascota, List<AntecedenteDto> alertas, List<AntecedenteDto> antecedentes,
            List<VacunaDto> vacunas, List<PreventivoDto> preventivos, List<ConsultaDto> consultas,
            List<RecetaDto> recetas, List<EstudioDto> estudios, List<DocumentoResumen> documentos) {}
    public record Creado(Integer id, String folio) {}

    // ---------------------------------------------------------------- ficha y búsqueda
    @GetMapping
    public List<ExpedienteService.ResultadoBusqueda> buscar(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String diagnostico,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.buscar(q, diagnostico, desde, hasta);
    }

    @GetMapping("/{mascotaId}")
    public FichaDto ficha(@PathVariable Integer mascotaId) {
        return dto(servicio.ficha(mascotaId));
    }

    @GetMapping("/refuerzos")
    public List<Map<String, Object>> refuerzos(@RequestParam(defaultValue = "30") int dias) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Vacuna v : servicio.refuerzos(dias)) {
            lista.add(Map.of("mascotaId", v.getMascota().getId(), "mascota", v.getMascota().getNombre(),
                    "dueno", v.getMascota().getDueno().getNombre(),
                    "telefono", Objects.toString(v.getMascota().getDueno().getTelefono(), ""),
                    "vacuna", v.getVacuna(), "proximoRefuerzo", v.getProximoRefuerzo()));
        }
        return lista;
    }

    // ---------------------------------------------------------------- altas
    @PostMapping("/{mascotaId}/vacunas") @ResponseStatus(HttpStatus.CREATED)
    public Creado vacuna(@PathVariable Integer mascotaId, @Valid @RequestBody VacunaRequest r, HttpSession s) {
        return new Creado(servicio.registrarVacuna(mascotaId, new ExpedienteService.VacunaDatos(r.vacuna(), r.lote(), r.dosis(),
                r.fechaAplicacion(), r.proximoRefuerzo(), r.observaciones()), sesion.usuario(s)).getId(), null);
    }
    @PostMapping("/{mascotaId}/preventivos") @ResponseStatus(HttpStatus.CREATED)
    public Creado preventivo(@PathVariable Integer mascotaId, @Valid @RequestBody PreventivoRequest r, HttpSession s) {
        return new Creado(servicio.registrarPreventivo(mascotaId, new ExpedienteService.PreventivoDatos(r.tipo(), r.producto(),
                r.dosis(), r.fechaAplicacion(), r.proximaAplicacion(), r.observaciones()), sesion.usuario(s)).getId(), null);
    }
    @PutMapping("/{tipo}/{id}/anular")
    public void anular(@PathVariable String tipo, @PathVariable Integer id, @Valid @RequestBody AnularRequest r, HttpSession s) {
        servicio.anular(tipo, id, r.motivo(), sesion.usuario(s));
    }
    @PostMapping("/{mascotaId}/antecedentes") @ResponseStatus(HttpStatus.CREATED)
    public Creado antecedente(@PathVariable Integer mascotaId, @Valid @RequestBody AntecedenteRequest r, HttpSession s) {
        return new Creado(servicio.registrarAntecedente(mascotaId,
                new ExpedienteService.AntecedenteDatos(r.tipo(), r.descripcion(), r.severidad()), sesion.usuario(s)).getId(), null);
    }
    @PutMapping("/antecedentes/{id}/cerrar")
    public void cerrarAntecedente(@PathVariable Integer id, HttpSession s) { servicio.cerrarAntecedente(id, sesion.usuario(s)); }

    @PostMapping("/consultas/{consultaId}/notas") @ResponseStatus(HttpStatus.CREATED)
    public Creado nota(@PathVariable Integer consultaId, @Valid @RequestBody NotaRequest r, HttpSession s) {
        return new Creado(servicio.agregarNota(consultaId, r.texto(), sesion.usuario(s)).getId(), null);
    }
    @PostMapping("/{mascotaId}/recetas") @ResponseStatus(HttpStatus.CREATED)
    public Creado receta(@PathVariable Integer mascotaId, @Valid @RequestBody RecetaRequest r, HttpSession s) {
        Receta receta = servicio.crearReceta(mascotaId, r.consultaId(), r.indicaciones(), r.medicamentos().stream()
                .map(x -> new ExpedienteService.RecetaRenglon(x.productoId(), x.medicamento(), x.dosis(), x.frecuencia(),
                        x.via(), x.duracion(), x.indicaciones())).toList(), sesion.usuario(s));
        return new Creado(receta.getId(), receta.getFolio());
    }
    @PostMapping("/{mascotaId}/estudios") @ResponseStatus(HttpStatus.CREATED)
    public Creado estudio(@PathVariable Integer mascotaId, @Valid @RequestBody EstudioRequest r, HttpSession s) {
        return new Creado(servicio.registrarEstudio(mascotaId, new ExpedienteService.EstudioDatos(r.consultaId(), r.tipo(),
                r.descripcion(), r.resultado(), r.fecha()), sesion.usuario(s)).getId(), null);
    }
    @PostMapping(path = "/{mascotaId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public Creado documento(@PathVariable Integer mascotaId, @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(required = false) Integer estudioId, HttpSession s) throws java.io.IOException {
        return new Creado(servicio.adjuntar(mascotaId, estudioId, archivo.getOriginalFilename(), archivo.getBytes(),
                sesion.usuario(s)).getId(), null);
    }
    @GetMapping("/documentos/{id}")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer id, HttpSession s) {
        DocumentoClinico d = servicio.documento(id, sesion.usuario(s));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.getNombreOriginal(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(d.getContenido());
    }

    // ---------------------------------------------------------------- PDF
    @GetMapping(value = "/recetas/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> recetaPdf(@PathVariable Integer id, HttpSession s) {
        Usuario lector = sesion.usuario(s);
        Receta r = servicio.receta(id);
        Mascota m = r.getMascota();
        try (Pdf pdf = new Pdf(false)) {
            pdf.encabezado(negocio.encabezado(), "Receta veterinaria", r.getFolio(), FECHA_HORA.format(r.getFecha()), r.getVeterinario().getNombre());
            pdf.campo("Paciente", m.getNombre() + " (" + m.getEspecie() + (m.getRaza() == null ? "" : ", " + m.getRaza()) + ")");
            pdf.campo("Peso", m.getPesoKg() == null ? null : m.getPesoKg() + " kg");
            pdf.campo("Propietario", m.getDueno().getNombre());
            pdf.seccion("Medicamentos");
            pdf.tabla(List.of("Medicamento", "Dosis", "Frecuencia", "Vía", "Duración", "Indicaciones"),
                    r.getDetalles().stream().map(d -> List.of(d.getMedicamento(), d.getDosis(), d.getFrecuencia(), d.getVia(),
                            d.getDuracion(), Objects.toString(d.getIndicaciones(), ""))).toList(),
                    new float[]{3, 1.5f, 1.8f, 1.2f, 1.4f, 3});
            if (r.getIndicacionesGenerales() != null) { pdf.seccion("Indicaciones generales"); pdf.texto(r.getIndicacionesGenerales()); }
            pdf.firma(r.getVeterinario().getNombre());
            byte[] bytes = pdf.bytes();
            return archivo(bytes, r.getFolio() + ".pdf", lector, "RECETA_PDF", "receta", id);
        }
    }

    @GetMapping(value = "/{mascotaId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> historialPdf(@PathVariable Integer mascotaId, HttpSession s) {
        Usuario lector = sesion.usuario(s);
        FichaDto f = dto(servicio.ficha(mascotaId));
        MascotaFicha m = f.mascota();
        String folio = "EXP-" + m.id() + "-" + reloj.ahora().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        try (Pdf pdf = new Pdf(false)) {
            pdf.encabezado(negocio.encabezado(), "Expediente clínico", folio, FECHA_HORA.format(reloj.ahora()), lector.getNombre());
            pdf.campo("Paciente", m.nombre() + " · " + m.especie() + (m.raza() == null ? "" : " · " + m.raza()));
            pdf.campo("Sexo / edad", (m.sexo() == null ? "—" : m.sexo()) + " / " + Objects.toString(m.edad(), "—"));
            pdf.campo("Identificación", m.identificacion());
            pdf.campo("Esterilizado", m.esterilizado() == null ? null : m.esterilizado() ? "Sí" : "No");
            pdf.campo("Propietario", m.dueno() + (m.telefono() == null ? "" : " · " + m.telefono()));
            pdf.seccion("Alergias y alertas vigentes");
            pdf.tabla(List.of("Tipo", "Descripción", "Severidad"), f.alertas().stream()
                    .map(a -> List.of(a.tipo().name(), a.descripcion(), Objects.toString(a.severidad(), ""))).toList(), new float[]{1.2f, 5, 1.3f});
            pdf.seccion("Vacunación");
            pdf.tabla(List.of("Fecha", "Vacuna", "Dosis", "Lote", "Próximo refuerzo", "Responsable"), f.vacunas().stream()
                    .filter(v -> !v.anulada()).map(v -> List.of(FECHA.format(v.fechaAplicacion()), v.vacuna(), Objects.toString(v.dosis(), ""),
                            Objects.toString(v.lote(), ""), v.proximoRefuerzo() == null ? "" : FECHA.format(v.proximoRefuerzo()), v.veterinario())).toList(), null);
            pdf.seccion("Desparasitación y preventivos");
            pdf.tabla(List.of("Fecha", "Tipo", "Producto", "Dosis", "Próxima"), f.preventivos().stream()
                    .filter(p -> !p.anulada()).map(p -> List.of(FECHA.format(p.fechaAplicacion()), p.tipo().name(), p.producto(),
                            Objects.toString(p.dosis(), ""), p.proximaAplicacion() == null ? "" : FECHA.format(p.proximaAplicacion()))).toList(), null);
            pdf.seccion("Consultas");
            pdf.tabla(List.of("Fecha", "Veterinario", "Motivo", "Diagnóstico", "Tratamiento"), f.consultas().stream()
                    .map(c -> List.of(FECHA_HORA.format(c.fecha()), c.veterinario(), c.motivo(), Objects.toString(c.diagnostico(), ""),
                            Objects.toString(c.tratamiento(), ""))).toList(), new float[]{1.4f, 1.6f, 2, 3, 3});
            pdf.seccion("Estudios");
            pdf.tabla(List.of("Fecha", "Estudio", "Resultado"), f.estudios().stream()
                    .map(e -> List.of(FECHA.format(e.fecha()), e.tipo(), Objects.toString(e.resultado(), ""))).toList(), new float[]{1.2f, 2, 6});
            pdf.nota("Documento con datos personales y clínicos. Manéjese de forma confidencial.");
            return archivo(pdf.bytes(), folio + ".pdf", lector, "EXPEDIENTE_PDF", "mascota", mascotaId);
        }
    }

    // ---------------------------------------------------------------- conversión
    private ResponseEntity<byte[]> archivo(byte[] bytes, String nombre, Usuario lector, String accion, String tabla, Integer id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(bytes);
    }

    private FichaDto dto(ExpedienteService.Ficha f) {
        Mascota m = f.mascota();
        Dueno d = m.getDueno();
        MascotaFicha mascota = new MascotaFicha(m.getId(), m.getNombre(), m.getEspecie(), m.getRaza(), m.getSexo(),
                m.getFechaNacimiento(), edad(m.getFechaNacimiento()), m.getPesoKg(), m.getIdentificacion(), m.getColor(),
                m.getEsterilizado(), m.getObservaciones(), d.getId(), d.getNombre(), d.getTelefono(), d.getCorreo());
        List<AntecedenteDto> antecedentes = f.antecedentes().stream().map(a -> new AntecedenteDto(a.getId(), a.getTipo(),
                a.getDescripcion(), a.getSeveridad(), a.isVigente(), a.getRegistradoPor().getNombre(), a.getCreadoEn())).toList();
        Map<Integer, List<NotaDto>> notas = new HashMap<>();
        f.notas().forEach(n -> notas.computeIfAbsent(n.getConsulta().getId(), k -> new ArrayList<>())
                .add(new NotaDto(n.getId(), n.getCreadoEn(), n.getAutor().getNombre(), n.getTexto())));
        return new FichaDto(mascota,
                antecedentes.stream().filter(a -> a.vigente() && (a.tipo() == AntecedenteClinico.Tipo.ALERGIA
                        || a.tipo() == AntecedenteClinico.Tipo.ALERTA || a.severidad() == AntecedenteClinico.Severidad.GRAVE)).toList(),
                antecedentes,
                f.vacunas().stream().map(v -> new VacunaDto(v.getId(), v.getVacuna(), v.getLote(), v.getDosis(), v.getFechaAplicacion(),
                        v.getProximoRefuerzo(), v.getVeterinario().getNombre(), v.getObservaciones(), v.isAnulada(), v.getMotivoAnulacion())).toList(),
                f.preventivos().stream().map(p -> new PreventivoDto(p.getId(), p.getTipo(), p.getProducto(), p.getDosis(),
                        p.getFechaAplicacion(), p.getProximaAplicacion(), p.getVeterinario().getNombre(), p.getObservaciones(),
                        p.isAnulada(), p.getMotivoAnulacion())).toList(),
                f.consultas().stream().map(c -> new ConsultaDto(c.getId(), c.getFecha(), c.getVeterinario().getNombre(), c.getMotivo(),
                        c.getPesoKg(), c.getTemperaturaC(), c.getFrecuenciaCardiaca(), c.getFrecuenciaRespiratoria(),
                        c.getCondicionCorporal(), c.getDiagnostico(), c.getTratamiento(), c.getEvolucion(),
                        notas.getOrDefault(c.getId(), List.of()))).toList(),
                f.recetas().stream().map(r -> new RecetaDto(r.getId(), r.getFolio(), r.getFecha(), r.getVeterinario().getNombre(),
                        r.getConsulta() == null ? null : r.getConsulta().getId(), r.getIndicacionesGenerales(),
                        r.getDetalles().stream().map(x -> new RenglonDto(x.getProducto() == null ? null : x.getProducto().getId(),
                                x.getMedicamento(), x.getDosis(), x.getFrecuencia(), x.getVia(), x.getDuracion(), x.getIndicaciones())).toList())).toList(),
                f.estudios().stream().map(e -> new EstudioDto(e.getId(), e.getFecha(), e.getTipo(), e.getDescripcion(), e.getResultado(),
                        e.getRegistradoPor().getNombre(), e.getConsulta() == null ? null : e.getConsulta().getId())).toList(),
                f.documentos());
    }

    private String edad(LocalDate nacimiento) {
        if (nacimiento == null) return null;
        Period p = Period.between(nacimiento, reloj.hoy());
        if (p.getYears() > 0) return p.getYears() + (p.getYears() == 1 ? " año" : " años") + (p.getMonths() > 0 ? " " + p.getMonths() + " m" : "");
        if (p.getMonths() > 0) return p.getMonths() + (p.getMonths() == 1 ? " mes" : " meses");
        return p.getDays() + " días";
    }
}
