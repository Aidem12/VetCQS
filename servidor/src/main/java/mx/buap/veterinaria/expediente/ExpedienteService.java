package mx.buap.veterinaria.expediente;

import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import mx.buap.veterinaria.comun.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

/** Expediente clínico: ficha médica, preventivos, antecedentes, recetas, estudios y adjuntos. */
@Service
public class ExpedienteService {
    static final int MAX_ADJUNTO = 5 * 1024 * 1024;
    private final MascotaRepository mascotas;
    private final ConsultaRepository consultas;
    private final ProductoRepository productos;
    private final VacunaRepository vacunas;
    private final TratamientoPreventivoRepository preventivos;
    private final AntecedenteClinicoRepository antecedentes;
    private final NotaClinicaRepository notas;
    private final RecetaRepository recetas;
    private final EstudioRepository estudios;
    private final DocumentoClinicoRepository documentos;
    private final AuditoriaService auditoria;
    private final EntityManager em;
    private final Reloj reloj;

    public ExpedienteService(MascotaRepository mascotas, ConsultaRepository consultas, ProductoRepository productos,
            VacunaRepository vacunas, TratamientoPreventivoRepository preventivos, AntecedenteClinicoRepository antecedentes,
            NotaClinicaRepository notas, RecetaRepository recetas, EstudioRepository estudios,
            DocumentoClinicoRepository documentos, AuditoriaService auditoria, EntityManager em, Reloj reloj) {
        this.mascotas = mascotas; this.consultas = consultas; this.productos = productos; this.vacunas = vacunas;
        this.preventivos = preventivos; this.antecedentes = antecedentes; this.notas = notas; this.recetas = recetas;
        this.estudios = estudios; this.documentos = documentos; this.auditoria = auditoria; this.em = em; this.reloj = reloj;
    }

    // ------------------------------------------------------------------ registros
    public record VacunaDatos(String vacuna, String lote, String dosis, LocalDate fechaAplicacion, LocalDate proximoRefuerzo,
            String observaciones) {}
    public record PreventivoDatos(TratamientoPreventivo.Tipo tipo, String producto, String dosis, LocalDate fechaAplicacion,
            LocalDate proximaAplicacion, String observaciones) {}
    public record AntecedenteDatos(AntecedenteClinico.Tipo tipo, String descripcion, AntecedenteClinico.Severidad severidad) {}
    public record RecetaRenglon(Integer productoId, String medicamento, String dosis, String frecuencia, String via,
            String duracion, String indicaciones) {}
    public record EstudioDatos(Integer consultaId, String tipo, String descripcion, String resultado, LocalDate fecha) {}

    @Transactional
    public Vacuna registrarVacuna(Integer mascotaId, VacunaDatos d, Usuario autor) {
        fechaValida(d.fechaAplicacion(), "aplicación");
        if (d.proximoRefuerzo() != null && d.proximoRefuerzo().isBefore(d.fechaAplicacion()))
            throw error(HttpStatus.BAD_REQUEST, "El próximo refuerzo no puede ser anterior a la aplicación.");
        Vacuna v = new Vacuna();
        v.setMascota(mascota(mascotaId)); v.setVacuna(requerido(d.vacuna(), "la vacuna", 120));
        v.setLote(opcional(d.lote(), 40)); v.setDosis(opcional(d.dosis(), 40));
        v.setFechaAplicacion(d.fechaAplicacion()); v.setProximoRefuerzo(d.proximoRefuerzo());
        v.setObservaciones(opcional(d.observaciones(), 500)); v.setVeterinario(autor);
        Vacuna guardada = vacunas.save(v);
        auditoria.registrar(autor.getId(), "VACUNA_ALTA", "vacuna", guardada.getId(), v.getVacuna() + " a mascota " + mascotaId);
        return guardada;
    }

    @Transactional
    public TratamientoPreventivo registrarPreventivo(Integer mascotaId, PreventivoDatos d, Usuario autor) {
        if (d.tipo() == null) throw error(HttpStatus.BAD_REQUEST, "Indique el tipo de tratamiento.");
        fechaValida(d.fechaAplicacion(), "aplicación");
        if (d.proximaAplicacion() != null && d.proximaAplicacion().isBefore(d.fechaAplicacion()))
            throw error(HttpStatus.BAD_REQUEST, "La próxima aplicación no puede ser anterior a la aplicación.");
        TratamientoPreventivo t = new TratamientoPreventivo();
        t.setMascota(mascota(mascotaId)); t.setTipo(d.tipo()); t.setProducto(requerido(d.producto(), "el producto", 120));
        t.setDosis(opcional(d.dosis(), 60)); t.setFechaAplicacion(d.fechaAplicacion()); t.setProximaAplicacion(d.proximaAplicacion());
        t.setObservaciones(opcional(d.observaciones(), 500)); t.setVeterinario(autor);
        TratamientoPreventivo guardado = preventivos.save(t);
        auditoria.registrar(autor.getId(), "PREVENTIVO_ALTA", "tratamiento_preventivo", guardado.getId(), t.getProducto());
        return guardado;
    }

    /** Anula (no borra) una vacuna o un tratamiento preventivo registrado por error. */
    @Transactional
    public void anular(String tipo, Integer id, String motivo, Usuario autor) {
        String razon = requerido(motivo, "el motivo de la anulación", 200);
        Anulable registro = switch (tipo) {
            case "vacunas" -> vacunas.findById(id).orElse(null);
            case "preventivos" -> preventivos.findById(id).orElse(null);
            default -> throw error(HttpStatus.BAD_REQUEST, "Tipo de registro inválido.");
        };
        if (registro == null) throw error(HttpStatus.NOT_FOUND, "Registro no encontrado.");
        if (registro.isAnulada()) throw error(HttpStatus.CONFLICT, "El registro ya estaba anulado.");
        registro.anular(autor, razon);
        auditoria.registrar(autor.getId(), "ANULACION", tipo, id, razon);
    }

    @Transactional
    public AntecedenteClinico registrarAntecedente(Integer mascotaId, AntecedenteDatos d, Usuario autor) {
        if (d.tipo() == null) throw error(HttpStatus.BAD_REQUEST, "Indique el tipo de antecedente.");
        AntecedenteClinico a = new AntecedenteClinico();
        a.setMascota(mascota(mascotaId)); a.setTipo(d.tipo()); a.setSeveridad(d.severidad());
        a.setDescripcion(requerido(d.descripcion(), "la descripción", 300)); a.setRegistradoPor(autor);
        AntecedenteClinico guardado = antecedentes.save(a);
        auditoria.registrar(autor.getId(), "ANTECEDENTE_ALTA", "antecedente_clinico", guardado.getId(), d.tipo() + ": " + a.getDescripcion());
        return guardado;
    }

    /** Marca un antecedente como ya no vigente; se conserva en el historial. */
    @Transactional
    public void cerrarAntecedente(Integer id, Usuario autor) {
        AntecedenteClinico a = antecedentes.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Antecedente no encontrado."));
        if (!a.isVigente()) throw error(HttpStatus.CONFLICT, "El antecedente ya estaba cerrado.");
        a.cerrar(autor, reloj.ahora());
        auditoria.registrar(autor.getId(), "ANTECEDENTE_CIERRE", "antecedente_clinico", id, a.getDescripcion());
    }

    @Transactional
    public NotaClinica agregarNota(Integer consultaId, String texto, Usuario autor) {
        NotaClinica n = new NotaClinica();
        n.setConsulta(consultas.findById(consultaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Consulta no encontrada.")));
        n.setTexto(requerido(texto, "el texto de la nota", 8000)); n.setAutor(autor);
        NotaClinica guardada = notas.save(n);
        auditoria.registrar(autor.getId(), "NOTA_CLINICA", "consulta", consultaId, "Nota " + guardada.getId());
        return guardada;
    }

    @Transactional
    public Receta crearReceta(Integer mascotaId, Integer consultaId, String indicaciones, List<RecetaRenglon> renglones, Usuario autor) {
        if (renglones == null || renglones.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Agregue al menos un medicamento.");
        if (renglones.size() > 30) throw error(HttpStatus.BAD_REQUEST, "Una receta admite como máximo 30 medicamentos.");
        Receta r = new Receta();
        Mascota m = mascota(mascotaId);
        r.setMascota(m);
        if (consultaId != null) {
            Consulta c = consultas.findById(consultaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Consulta no encontrada."));
            if (!c.getMascota().getId().equals(m.getId())) throw error(HttpStatus.CONFLICT, "La consulta pertenece a otra mascota.");
            r.setConsulta(c);
        }
        r.setVeterinario(autor);
        r.setIndicacionesGenerales(opcional(indicaciones, 4000));
        for (RecetaRenglon x : renglones) {
            RecetaDetalle d = new RecetaDetalle();
            if (x.productoId() != null)
                d.setProducto(productos.findById(x.productoId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Medicamento no encontrado.")));
            d.setMedicamento(requerido(x.medicamento() != null ? x.medicamento() : d.getProducto() == null ? null : d.getProducto().getNombre(),
                    "el medicamento", 160));
            d.setDosis(requerido(x.dosis(), "la dosis", 80)); d.setFrecuencia(requerido(x.frecuencia(), "la frecuencia", 80));
            d.setVia(requerido(x.via(), "la vía de administración", 40)); d.setDuracion(requerido(x.duracion(), "la duración", 60));
            d.setIndicaciones(opcional(x.indicaciones(), 400));
            r.agregar(d);
        }
        r.setFolio(String.format("R-%06d", recetas.siguienteFolio()));
        Receta guardada = recetas.save(r);
        auditoria.registrar(autor.getId(), "RECETA_ALTA", "receta", guardada.getId(), guardada.getFolio());
        return guardada;
    }

    @Transactional
    public Estudio registrarEstudio(Integer mascotaId, EstudioDatos d, Usuario autor) {
        fechaValida(d.fecha(), "estudio");
        Estudio e = new Estudio();
        Mascota m = mascota(mascotaId);
        e.setMascota(m);
        if (d.consultaId() != null) {
            Consulta c = consultas.findById(d.consultaId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Consulta no encontrada."));
            if (!c.getMascota().getId().equals(m.getId())) throw error(HttpStatus.CONFLICT, "La consulta pertenece a otra mascota.");
            e.setConsulta(c);
        }
        e.setTipo(requerido(d.tipo(), "el tipo de estudio", 80)); e.setDescripcion(opcional(d.descripcion(), 300));
        e.setResultado(opcional(d.resultado(), 20000)); e.setFecha(d.fecha()); e.setRegistradoPor(autor);
        Estudio guardado = estudios.save(e);
        auditoria.registrar(autor.getId(), "ESTUDIO_ALTA", "estudio", guardado.getId(), e.getTipo());
        return guardado;
    }

    /** Valida tamaño y tipo real (por su firma de bytes, no solo por la extensión) antes de guardar. */
    @Transactional
    public DocumentoClinico adjuntar(Integer mascotaId, Integer estudioId, String nombre, byte[] contenido, Usuario autor) {
        if (contenido == null || contenido.length == 0) throw error(HttpStatus.BAD_REQUEST, "El archivo está vacío.");
        if (contenido.length > MAX_ADJUNTO) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera 5 MB.");
        String mime = tipoReal(contenido);
        if (mime == null) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se aceptan archivos PDF, PNG o JPEG.");
        DocumentoClinico doc = new DocumentoClinico();
        Mascota m = mascota(mascotaId);
        doc.setMascota(m);
        if (estudioId != null) {
            Estudio e = estudios.findById(estudioId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Estudio no encontrado."));
            if (!e.getMascota().getId().equals(m.getId())) throw error(HttpStatus.CONFLICT, "El estudio pertenece a otra mascota.");
            doc.setEstudio(e);
        }
        String limpio = nombre == null || nombre.isBlank() ? "documento" : nombre.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        doc.setNombreOriginal(limpio.length() > 200 ? limpio.substring(0, 200) : limpio);
        doc.setTipoMime(mime); doc.setTamanoBytes(contenido.length); doc.setSha256(sha256(contenido));
        doc.setContenido(contenido); doc.setSubidoPor(autor);
        DocumentoClinico guardado = documentos.save(doc);
        auditoria.registrar(autor.getId(), "ADJUNTO_ALTA", "documento_clinico", guardado.getId(), doc.getNombreOriginal());
        return guardado;
    }

    @Transactional(readOnly = true)
    public DocumentoClinico documento(Integer id, Usuario lector) {
        DocumentoClinico d = documentos.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Documento no encontrado."));
        d.getContenido();
        auditoria.registrar(lector.getId(), "ADJUNTO_LECTURA", "documento_clinico", id, null);
        return d;
    }

    // ------------------------------------------------------------------ consultas

    public record Ficha(Mascota mascota, List<AntecedenteClinico> antecedentes, List<Vacuna> vacunas,
            List<TratamientoPreventivo> preventivos, List<Consulta> consultas, List<NotaClinica> notas,
            List<Receta> recetas, List<Estudio> estudios, List<DocumentoResumen> documentos) {}

    @Transactional(readOnly = true)
    public Ficha ficha(Integer mascotaId) {
        Mascota m = mascota(mascotaId);
        m.getDueno().getNombre();
        return new Ficha(m, antecedentes.findByMascotaIdOrderByVigenteDescCreadoEnDesc(mascotaId),
                vacunas.findByMascotaIdOrderByFechaAplicacionDesc(mascotaId),
                preventivos.findByMascotaIdOrderByFechaAplicacionDesc(mascotaId),
                consultas.findByMascotaIdOrderByFechaDesc(mascotaId),
                notas.findByConsultaMascotaIdOrderByCreadoEnAsc(mascotaId),
                recetas.findByMascotaIdOrderByFechaDesc(mascotaId),
                estudios.findByMascotaIdOrderByFechaDesc(mascotaId), documentos.resumen(mascotaId));
    }

    @Transactional(readOnly = true)
    public Receta receta(Integer id) {
        Receta r = recetas.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Receta no encontrada."));
        r.getDetalles().size(); r.getMascota().getDueno().getNombre(); r.getVeterinario().getNombre();
        return r;
    }

    public record ResultadoBusqueda(Integer mascotaId, String mascota, String especie, Integer duenoId, String dueno,
            String telefono, LocalDateTime ultimaConsulta, String ultimoDiagnostico) {}

    /** Busca expedientes por mascota o dueño, diagnóstico y fecha de consulta. Devuelve como máximo 100. */
    @Transactional(readOnly = true)
    public List<ResultadoBusqueda> buscar(String texto, String diagnostico, LocalDate desde, LocalDate hasta) {
        String q = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        String dx = diagnostico == null ? "" : diagnostico.trim().toLowerCase(Locale.ROOT);
        boolean porConsulta = !dx.isEmpty() || desde != null || hasta != null;
        String jpql = porConsulta
            ? """
              select c from Consulta c join fetch c.mascota m join fetch m.dueno d
              where (:q = '' or lower(m.nombre) like :qp or lower(d.nombre) like :qp or d.telefono like :qp)
                and (:dx = '' or lower(coalesce(c.diagnostico, '')) like :dxp)
                and c.fecha >= :desde and c.fecha < :hasta
              order by c.fecha desc"""
            : null;
        Map<Integer, ResultadoBusqueda> resultado = new LinkedHashMap<>();
        if (porConsulta) {
            var query = em.createQuery(jpql, Consulta.class)
                    .setParameter("q", q).setParameter("qp", "%" + q + "%")
                    .setParameter("dx", dx).setParameter("dxp", "%" + dx + "%")
                    .setParameter("desde", (desde == null ? LocalDate.of(1900, 1, 1) : desde).atStartOfDay())
                    .setParameter("hasta", (hasta == null ? LocalDate.of(3000, 1, 1) : hasta.plusDays(1)).atStartOfDay())
                    .setMaxResults(500);
            for (Consulta c : query.getResultList()) {
                Mascota m = c.getMascota();
                resultado.putIfAbsent(m.getId(), new ResultadoBusqueda(m.getId(), m.getNombre(), m.getEspecie(),
                        m.getDueno().getId(), m.getDueno().getNombre(), m.getDueno().getTelefono(), c.getFecha(), c.getDiagnostico()));
                if (resultado.size() >= 100) break;
            }
        } else {
            var query = em.createQuery("""
                select m from Mascota m join fetch m.dueno d
                where (:q = '' or lower(m.nombre) like :qp or lower(d.nombre) like :qp or d.telefono like :qp)
                order by m.nombre""", Mascota.class)
                    .setParameter("q", q).setParameter("qp", "%" + q + "%").setMaxResults(100);
            for (Mascota m : query.getResultList()) {
                Consulta ultima = consultas.findByMascotaIdOrderByFechaDesc(m.getId()).stream().findFirst().orElse(null);
                resultado.put(m.getId(), new ResultadoBusqueda(m.getId(), m.getNombre(), m.getEspecie(), m.getDueno().getId(),
                        m.getDueno().getNombre(), m.getDueno().getTelefono(), ultima == null ? null : ultima.getFecha(),
                        ultima == null ? null : ultima.getDiagnostico()));
            }
        }
        return new ArrayList<>(resultado.values());
    }

    /** Recordatorios de refuerzos de vacuna en los próximos días. */
    @Transactional(readOnly = true)
    public List<Vacuna> refuerzos(int dias) {
        LocalDate hoy = reloj.hoy();
        List<Vacuna> lista = vacunas.refuerzosEntre(hoy, hoy.plusDays(Math.max(1, Math.min(dias, 365))));
        lista.forEach(v -> v.getMascota().getDueno().getNombre());
        return lista;
    }

    // ------------------------------------------------------------------ apoyo

    static String tipoReal(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }
    static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void fechaValida(LocalDate fecha, String que) {
        if (fecha == null) throw error(HttpStatus.BAD_REQUEST, "Indique la fecha de " + que + ".");
        if (fecha.isAfter(reloj.hoy())) throw error(HttpStatus.BAD_REQUEST, "La fecha de " + que + " no puede ser futura.");
        if (fecha.isBefore(LocalDate.of(1990, 1, 1))) throw error(HttpStatus.BAD_REQUEST, "La fecha de " + que + " es demasiado antigua.");
    }
    private Mascota mascota(Integer id) {
        return mascotas.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Mascota no encontrada."));
    }
    private static String requerido(String texto, String que, int max) {
        if (texto == null || texto.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Capture " + que + ".");
        String t = texto.trim();
        if (t.length() > max) throw error(HttpStatus.BAD_REQUEST, "El texto de " + que + " es demasiado largo (máximo " + max + ").");
        return t;
    }
    private static String opcional(String texto, int max) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();
        if (t.length() > max) throw error(HttpStatus.BAD_REQUEST, "Un texto supera el máximo de " + max + " caracteres.");
        return t;
    }
    private static ResponseStatusException error(HttpStatus s, String m) { return new ResponseStatusException(s, m); }
}
