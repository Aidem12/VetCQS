package mx.buap.veterinaria.portal;

import java.io.File;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import static mx.buap.veterinaria.portal.Tablas.*;

/**
 * Panel de indicadores: ingresos netos (ventas pagadas menos devoluciones), ventas por día, métodos de pago,
 * productos y servicios más vendidos, consultas, citas, caja y alertas; exportación a PDF y Excel con los mismos filtros.
 */
final class IndicadoresPane {
    private final Api api;
    private final Stage owner;
    private final DatePicker desde = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker hasta = new DatePicker(LocalDate.now());
    private final ComboBox<JsonNode> usuario = new ComboBox<>();
    private final ComboBox<String> tipo = new ComboBox<>(FXCollections.observableArrayList("Todos", "PRODUCTO", "SERVICIO"));
    private final FlowPane tarjetas = new FlowPane(10, 10);
    private final LineChart<String, Number> serie = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final PieChart metodos = new PieChart();
    private final BarChart<Number, String> top = new BarChart<>(new NumberAxis(), new CategoryAxis());
    private final VBox alertas = new VBox(4);
    private final Label caja = new Label();
    private final Label aviso = new Label();

    IndicadoresPane(Api api, Stage owner) { this.api = api; this.owner = owner; }

    Node crear() {
        tipo.setValue("Todos");
        usuario.setPromptText("Todos los cajeros");
        usuario.setCellFactory(v -> celda()); usuario.setButtonCell(celda());
        Button consultar = new Button("Consultar"); consultar.getStyleClass().add("primary"); consultar.setOnAction(e -> cargar());
        Button pdf = new Button("Exportar PDF"); pdf.setOnAction(e -> exportar("pdf"));
        Button excel = new Button("Exportar Excel"); excel.setOnAction(e -> exportar("xlsx"));
        HBox filtros = new HBox(8, new Label("Del"), desde, new Label("al"), hasta, usuario, tipo, consultar, pdf, excel);
        filtros.setAlignment(Pos.CENTER_LEFT);
        serie.setTitle("Ingresos por día (ventas pagadas)"); serie.setLegendVisible(false); serie.setCreateSymbols(true); serie.setAnimated(false);
        metodos.setTitle("Métodos de pago"); metodos.setLegendVisible(true); metodos.setAnimated(false);
        top.setTitle("Más vendidos (importe)"); top.setLegendVisible(false); top.setAnimated(false);
        serie.setMinHeight(260); metodos.setMinHeight(260); top.setMinHeight(300);
        GridPane graficas = new GridPane(); graficas.setHgap(10); graficas.setVgap(10);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(60); ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(40);
        graficas.getColumnConstraints().addAll(c1, c2);
        graficas.add(serie, 0, 0); graficas.add(metodos, 1, 0); graficas.add(top, 0, 1);
        caja.setWrapText(true);
        VBox lateral = new VBox(8, titulo("CAJA"), caja, titulo("ALERTAS"), alertas); lateral.setPadding(new Insets(6));
        graficas.add(lateral, 1, 1);
        aviso.setWrapText(true);
        VBox contenido = new VBox(12, filtros, tarjetas, graficas, aviso);
        contenido.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(contenido); scroll.setFitToWidth(true);
        VBox raiz = new VBox(10, titulo("PANEL DE INDICADORES"), scroll); VBox.setVgrow(scroll, Priority.ALWAYS);
        raiz.getStyleClass().add("card");
        return raiz;
    }

    void cargar() {
        if (usuario.getItems().isEmpty()) api.get("/api/empleados", lista -> {
            usuario.getItems().add(api.json.nullNode());
            lista.forEach(u -> { if (!"VETERINARIO".equals(u.path("rol").asText())) usuario.getItems().add(u); });
        }, m -> {});
        api.get("/api/reportes/dashboard?" + filtros(), this::pintar, this::error);
    }

    private String filtros() {
        StringBuilder q = new StringBuilder("desde=" + desde.getValue() + "&hasta=" + hasta.getValue());
        if (usuario.getValue() != null && !usuario.getValue().isNull()) q.append("&usuarioId=").append(usuario.getValue().path("id").asInt());
        if (!"Todos".equals(tipo.getValue())) q.append("&tipo=").append(tipo.getValue());
        return q.toString();
    }

    private void pintar(JsonNode d) {
        tarjetas.getChildren().setAll(
                tarjeta("Hoy", d.path("hoy")), tarjeta("Esta semana", d.path("semana")), tarjeta("Este mes", d.path("mes")), tarjeta("Periodo filtrado", d.path("periodo")),
                simple("Consultas del periodo", d.path("consultas").asText()), simple("Productos bajo mínimo", d.path("productosBajoMinimo").asText()),
                simple("Lotes por caducar (30 días)", d.path("lotesPorCaducar").asText()));
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        d.path("serie").forEach(p -> s.getData().add(new XYChart.Data<>(p.path("fecha").asText().substring(5), p.path("ingresos").decimalValue())));
        serie.getData().setAll(s);
        metodos.getData().clear();
        d.path("metodosPago").forEach(m -> metodos.getData().add(new PieChart.Data(m.path("etiqueta").asText() + " " + dinero(m.path("importe")), m.path("importe").doubleValue())));
        XYChart.Series<Number, String> t = new XYChart.Series<>();
        java.util.List<JsonNode> vendidos = new java.util.ArrayList<>(); d.path("masVendidos").forEach(vendidos::add);
        java.util.Collections.reverse(vendidos);
        vendidos.forEach(v -> t.getData().add(new XYChart.Data<>(v.path("importe").decimalValue(), recortar(v.path("producto").asText()))));
        top.getData().setAll(t);
        JsonNode c = d.path("caja");
        StringBuilder citas = new StringBuilder();
        d.path("citasPorEstado").forEach(x -> citas.append(x.path("etiqueta").asText().replace('_', ' ')).append(": ").append(x.path("cantidad").asText()).append("   "));
        caja.setText("Turnos: " + c.path("turnos").asText() + " · con diferencia: " + c.path("turnosConDiferencia").asText()
                + "\nSuma de diferencias: " + dinero(c.path("sumaDiferencias")) + "\nSalidas de efectivo: " + dinero(c.path("salidasEfectivo"))
                + "\nDevoluciones del periodo: " + dinero(d.path("periodo").path("devoluciones"))
                + "\n\nCitas: " + (citas.length() == 0 ? "sin citas" : citas.toString()));
        alertas.getChildren().clear();
        d.path("alertas").forEach(a -> {
            Label l = new Label(a.path("tipo").asText().replace('_', ' ') + " · " + a.path("descripcion").asText() + "\n" + a.path("detalle").asText());
            l.setWrapText(true); l.setStyle("-fx-text-fill: " + ("CADUCADO".equals(a.path("tipo").asText()) ? "#B3261E" : "#8A5A00") + ";");
            alertas.getChildren().add(l);
        });
        if (alertas.getChildren().isEmpty()) alertas.getChildren().add(new Label("Sin alertas."));
        aviso.setText("Ingresos = ventas pagadas menos devoluciones. Se pueden conciliar con el historial de cortes.");
        aviso.setStyle("-fx-text-fill: #425466;");
    }

    private void exportar(String formato) {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("indicadores-" + desde.getValue() + "-a-" + hasta.getValue() + "." + formato);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(formato.equals("pdf") ? "PDF" : "Excel", "*." + formato));
        File f = fc.showSaveDialog(owner); if (f == null) return;
        api.descargar("/api/reportes/dashboard." + formato + "?" + filtros(), f.toPath(), () -> { aviso.setText("Reporte guardado en " + f.getAbsolutePath()); aviso.setStyle("-fx-text-fill: #21865B;"); abrir(f); }, this::error);
    }

    private static VBox tarjeta(String titulo, JsonNode t) {
        Label valor = new Label(dinero(t.path("ingresos"))); valor.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #17324D;");
        Label det = new Label(t.path("ventas").asText() + " ventas · ticket prom. " + dinero(t.path("ticketPromedio"))); det.getStyleClass().add("muted");
        return marco(titulo, valor, det);
    }
    private static VBox simple(String titulo, String valor) {
        Label v = new Label(valor); v.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #17324D;");
        return marco(titulo, v);
    }
    private static VBox marco(String titulo, Node... nodos) {
        Label t = new Label(titulo.toUpperCase()); t.getStyleClass().add("section-title");
        VBox v = new VBox(4, t); v.getChildren().addAll(nodos); v.setPadding(new Insets(10)); v.setPrefWidth(220);
        v.setStyle("-fx-background-color: white; -fx-border-color: #D7E0E8; -fx-background-radius: 8; -fx-border-radius: 8;");
        return v;
    }
    private static String recortar(String s) { return s.length() > 26 ? s.substring(0, 25) + "…" : s; }
    private static ListCell<JsonNode> celda() {
        return new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e);
            setText(e ? null : n == null || n.isNull() ? "Todos los cajeros" : n.path("nombre").asText()); } };
    }
    private void error(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #B3261E;"); }
}
