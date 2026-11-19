package mx.buap.veterinaria.reportes;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import mx.buap.veterinaria.comun.*;
import mx.buap.veterinaria.model.Usuario;

/** Dashboard y exportación a PDF/Excel. Solo ADMINISTRADOR (SecurityConfig: /api/reportes/**). */
@RestController
@RequestMapping("/api/reportes")
public class ReportesController {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final DashboardService dashboard;
    private final SesionActual sesion;
    private final Negocio negocio;
    private final AuditoriaService auditoria;
    private final Reloj reloj;

    public ReportesController(DashboardService dashboard, SesionActual sesion, Negocio negocio, AuditoriaService auditoria, Reloj reloj) {
        this.dashboard = dashboard; this.sesion = sesion; this.negocio = negocio; this.auditoria = auditoria; this.reloj = reloj;
    }

    @GetMapping("/dashboard")
    public DashboardService.Dashboard dashboard(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer usuarioId, @RequestParam(required = false) Integer productoId,
            @RequestParam(required = false) String tipo) {
        return dashboard.calcular(new DashboardService.Filtros(desde, hasta, usuarioId, productoId, tipo));
    }

    @GetMapping(value = "/dashboard.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer usuarioId, @RequestParam(required = false) Integer productoId,
            @RequestParam(required = false) String tipo, HttpSession s) {
        Usuario u = sesion.usuario(s);
        var d = dashboard.calcular(new DashboardService.Filtros(desde, hasta, usuarioId, productoId, tipo));
        String folio = folio();
        try (Pdf pdf = new Pdf(false)) {
            pdf.encabezado(negocio.encabezado(), "Reporte de indicadores", folio, FH.format(reloj.ahora()), u.getNombre());
            pdf.campo("Periodo", F.format(desde) + " al " + F.format(hasta));
            pdf.campo("Filtros", filtros(d.filtros()));
            pdf.seccion("Ingresos");
            pdf.tabla(List.of("Periodo", "Ingresos netos", "Ventas", "Ticket promedio", "Devoluciones"), List.of(
                    fila("Hoy", d.hoy()), fila("Semana", d.semana()), fila("Mes", d.mes()), fila("Periodo filtrado", d.periodo())), null);
            pdf.seccion("Ventas por método de pago");
            pdf.tabla(List.of("Método", "Importe", "Pagos"), d.metodosPago().stream()
                    .map(m -> List.of(m.etiqueta(), dinero(m.importe()), String.valueOf(m.cantidad()))).toList(), null);
            pdf.seccion("Productos y servicios más vendidos");
            pdf.tabla(List.of("Producto / servicio", "Tipo", "Cantidad", "Importe"), d.masVendidos().stream()
                    .map(v -> List.of(v.producto(), v.tipo(), num(v.cantidad()), dinero(v.importe()))).toList(), new float[]{4, 1.4f, 1.2f, 1.6f});
            pdf.seccion("Consultas y citas");
            pdf.campo("Consultas registradas", String.valueOf(d.consultas()));
            pdf.tabla(List.of("Estado de cita", "Cantidad"), d.citasPorEstado().stream()
                    .map(c -> List.of(c.etiqueta(), String.valueOf(c.cantidad()))).toList(), null);
            pdf.seccion("Caja");
            pdf.tabla(List.of("Turnos", "Con diferencia", "Suma de diferencias", "Salidas de efectivo"), List.of(List.of(
                    String.valueOf(d.caja().turnos()), String.valueOf(d.caja().turnosConDiferencia()),
                    dinero(d.caja().sumaDiferencias()), dinero(d.caja().salidasEfectivo()))), null);
            pdf.seccion("Alertas de inventario");
            pdf.tabla(List.of("Tipo", "Producto", "Detalle"), d.alertas().stream()
                    .map(a -> List.of(a.tipo(), a.descripcion(), a.detalle())).toList(), new float[]{1.4f, 3, 4});
            pdf.nota("Cifras calculadas a partir de las ventas pagadas, pagos, devoluciones, consultas, citas y turnos registrados.");
            auditoria.registrar(u.getId(), "REPORTE_PDF", "reporte", 0, folio + " · " + filtros(d.filtros()));
            return archivo(pdf.bytes(), folio + ".pdf", MediaType.APPLICATION_PDF);
        }
    }

    @GetMapping("/dashboard.xlsx")
    public ResponseEntity<byte[]> excel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer usuarioId, @RequestParam(required = false) Integer productoId,
            @RequestParam(required = false) String tipo, HttpSession s) throws java.io.IOException {
        Usuario u = sesion.usuario(s);
        var d = dashboard.calcular(new DashboardService.Filtros(desde, hasta, usuarioId, productoId, tipo));
        String folio = folio();
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont(); fuente.setBold(true); negrita.setFont(fuente);
            CellStyle moneda = libro.createCellStyle();
            moneda.setDataFormat(libro.createDataFormat().getFormat("$#,##0.00"));

            Sheet resumen = libro.createSheet("Resumen");
            int r = 0;
            r = filaTexto(resumen, r, negrita, "Reporte de indicadores", negocio.getNombre());
            r = filaTexto(resumen, r, null, "Folio", folio);
            r = filaTexto(resumen, r, null, "Generado", FH.format(reloj.ahora()));
            r = filaTexto(resumen, r, null, "Responsable", u.getNombre());
            r = filaTexto(resumen, r, null, "Periodo", F.format(desde) + " al " + F.format(hasta));
            r = filaTexto(resumen, r, null, "Filtros", filtros(d.filtros()));
            r++;
            r = encabezados(resumen, r, negrita, "Periodo", "Ingresos netos", "Ventas", "Ticket promedio", "Devoluciones");
            for (var e : List.of(Map.entry("Hoy", d.hoy()), Map.entry("Semana", d.semana()), Map.entry("Mes", d.mes()), Map.entry("Periodo filtrado", d.periodo()))) {
                Row fila = resumen.createRow(r++);
                fila.createCell(0).setCellValue(e.getKey());
                celdaDinero(fila, 1, e.getValue().ingresos(), moneda);
                fila.createCell(2).setCellValue(e.getValue().ventas());
                celdaDinero(fila, 3, e.getValue().ticketPromedio(), moneda);
                celdaDinero(fila, 4, e.getValue().devoluciones(), moneda);
            }
            r++;
            r = encabezados(resumen, r, negrita, "Consultas", "Turnos", "Turnos con diferencia", "Suma de diferencias", "Salidas de efectivo");
            Row cj = resumen.createRow(r);
            cj.createCell(0).setCellValue(d.consultas()); cj.createCell(1).setCellValue(d.caja().turnos());
            cj.createCell(2).setCellValue(d.caja().turnosConDiferencia());
            celdaDinero(cj, 3, d.caja().sumaDiferencias(), moneda); celdaDinero(cj, 4, d.caja().salidasEfectivo(), moneda);
            for (int c = 0; c < 5; c++) resumen.setColumnWidth(c, 22 * 256);

            Sheet diario = libro.createSheet("Ingresos por día");
            int rd = encabezados(diario, 0, negrita, "Fecha", "Ingresos", "Ventas");
            for (var p : d.serie()) {
                Row fila = diario.createRow(rd++);
                fila.createCell(0).setCellValue(F.format(p.fecha())); celdaDinero(fila, 1, p.ingresos(), moneda); fila.createCell(2).setCellValue(p.ventas());
            }
            Sheet pagos = libro.createSheet("Métodos de pago");
            int rp = encabezados(pagos, 0, negrita, "Método", "Importe", "Pagos");
            for (var m : d.metodosPago()) {
                Row fila = pagos.createRow(rp++);
                fila.createCell(0).setCellValue(m.etiqueta()); celdaDinero(fila, 1, m.importe(), moneda); fila.createCell(2).setCellValue(m.cantidad());
            }
            Sheet top = libro.createSheet("Más vendidos");
            int rt = encabezados(top, 0, negrita, "Producto / servicio", "Tipo", "Cantidad", "Importe");
            for (var v : d.masVendidos()) {
                Row fila = top.createRow(rt++);
                fila.createCell(0).setCellValue(v.producto()); fila.createCell(1).setCellValue(v.tipo());
                fila.createCell(2).setCellValue(v.cantidad().doubleValue()); celdaDinero(fila, 3, v.importe(), moneda);
            }
            Sheet alertas = libro.createSheet("Alertas");
            int ra = encabezados(alertas, 0, negrita, "Tipo", "Producto", "Detalle");
            for (var a : d.alertas()) {
                Row fila = alertas.createRow(ra++);
                fila.createCell(0).setCellValue(a.tipo()); fila.createCell(1).setCellValue(a.descripcion()); fila.createCell(2).setCellValue(a.detalle());
            }
            for (Sheet hoja : List.of(diario, pagos, top, alertas)) for (int c = 0; c < 4; c++) hoja.setColumnWidth(c, 26 * 256);
            libro.write(salida);
            auditoria.registrar(u.getId(), "REPORTE_EXCEL", "reporte", 0, folio + " · " + filtros(d.filtros()));
            return archivo(salida.toByteArray(), folio + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        }
    }

    // ---------------------------------------------------------------- apoyo
    private String folio() { return "REP-" + reloj.ahora().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")); }
    private static String filtros(DashboardService.Filtros f) {
        List<String> partes = new ArrayList<>();
        if (f.usuarioId() != null) partes.add("usuario " + f.usuarioId());
        if (f.productoId() != null) partes.add("producto " + f.productoId());
        if (f.tipo() != null) partes.add("tipo " + f.tipo());
        return partes.isEmpty() ? "ninguno" : String.join(", ", partes);
    }
    private static List<String> fila(String nombre, DashboardService.Totales t) {
        return List.of(nombre, dinero(t.ingresos()), String.valueOf(t.ventas()), dinero(t.ticketPromedio()), dinero(t.devoluciones()));
    }
    private static String dinero(BigDecimal v) { return v == null ? "" : String.format(Locale.forLanguageTag("es-MX"), "$%,.2f", v); }
    private static String num(BigDecimal v) { return v == null ? "" : v.stripTrailingZeros().toPlainString(); }
    private static int filaTexto(Sheet hoja, int r, CellStyle estilo, String etiqueta, String valor) {
        Row fila = hoja.createRow(r);
        Cell c = fila.createCell(0); c.setCellValue(etiqueta); if (estilo != null) c.setCellStyle(estilo);
        fila.createCell(1).setCellValue(valor);
        return r + 1;
    }
    private static int encabezados(Sheet hoja, int r, CellStyle estilo, String... titulos) {
        Row fila = hoja.createRow(r);
        for (int i = 0; i < titulos.length; i++) { Cell c = fila.createCell(i); c.setCellValue(titulos[i]); c.setCellStyle(estilo); }
        return r + 1;
    }
    private static void celdaDinero(Row fila, int col, BigDecimal valor, CellStyle estilo) {
        Cell c = fila.createCell(col); c.setCellValue(valor == null ? 0 : valor.doubleValue()); c.setCellStyle(estilo);
    }
    private static ResponseEntity<byte[]> archivo(byte[] bytes, String nombre, MediaType tipo) {
        return ResponseEntity.ok().contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString()).body(bytes);
    }
}
