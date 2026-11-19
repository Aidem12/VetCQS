package mx.buap.veterinaria.consultorio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Agenda de citas del consultorio: vista por día, semana o mes, filtros, alta con horarios
 * disponibles (el servidor impide traslapes), cambios de estado y reprogramación con historial.
 */
final class AgendaView {
    private static final Locale MX = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEE dd/MM", MX);
    private final Api api;
    private final Stage ventana = new Stage();
    private final JsonNode mascotaInicial;
    private final DatePicker fecha = new DatePicker(LocalDate.now());
    private final ComboBox<String> vista = new ComboBox<>(FXCollections.observableArrayList("Día", "Semana", "Mes"));
    private final ComboBox<JsonNode> veterinario = new ComboBox<>();
    private final ComboBox<String> estado = new ComboBox<>(FXCollections.observableArrayList("Todos", "PENDIENTE", "CONFIRMADA", "EN_CURSO", "ATENDIDA", "CANCELADA", "NO_ASISTIO"));
    private final TextField buscar = new TextField();
    private final TableView<JsonNode> tabla = new TableView<>();
    private final GridPane mes = new GridPane();
    private final StackPane centro = new StackPane();
    private final Label aviso = new Label();
    private final Label recordatorios = new Label();
    private final List<JsonNode> tipos = new ArrayList<>();
    private int version;

    AgendaView(Api api, Stage owner, JsonNode mascotaInicial, String tema) {
        this.api = api; this.mascotaInicial = mascotaInicial;
        ventana.initOwner(owner);
        ventana.setTitle("Agenda de citas");
        vista.setValue("Día"); estado.setValue("Todos");
        buscar.setPromptText("Mascota, dueño o teléfono");
        veterinario.setPromptText("Todos los veterinarios");
        veterinario.setCellFactory(v -> celda()); veterinario.setButtonCell(celda());
        Button hoy = new Button("Hoy"); hoy.setOnAction(e -> fecha.setValue(LocalDate.now()));
        Button anterior = new Button("◀"), siguiente = new Button("▶");
        anterior.setOnAction(e -> mover(-1)); siguiente.setOnAction(e -> mover(1));
        Button nueva = new Button("Nueva cita"); nueva.getStyleClass().add("primary"); nueva.setOnAction(e -> nuevaCita());
        Button actualizar = new Button("Actualizar"); actualizar.setOnAction(e -> cargar());
        for (var c : List.of(fecha.valueProperty(), vista.valueProperty(), veterinario.valueProperty(), estado.valueProperty()))
            c.addListener((o, a, b) -> cargar());
        buscar.setOnAction(e -> cargar());
        HBox filtros = new HBox(8, anterior, hoy, siguiente, fecha, vista, veterinario, estado, buscar, actualizar, nueva);
        filtros.setAlignment(Pos.CENTER_LEFT); filtros.setPadding(new Insets(0, 0, 8, 0));
        HBox.setHgrow(buscar, Priority.ALWAYS);

        columna("Fecha", 95, n -> fechaHora(n.path("inicio").asText()).format(DIA));
        columna("Hora", 95, n -> fechaHora(n.path("inicio").asText()).format(HORA) + "–" + fechaHora(n.path("fin").asText()).format(HORA));
        columna("Mascota", 130, n -> n.path("mascota").asText() + " (" + n.path("especie").asText() + ")");
        columna("Dueño", 140, n -> n.path("dueno").asText());
        columna("Teléfono", 100, n -> n.path("telefono").asText(""));
        columna("Veterinario", 130, n -> n.path("veterinario").asText());
        columna("Tipo", 120, n -> n.path("tipo").asText());
        columna("Estado", 100, n -> n.path("estado").asText().replace('_', ' '));
        columna("Motivo", 200, n -> n.path("motivo").asText(""));
        tabla.setPlaceholder(new Label("No hay citas con estos filtros."));
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(JsonNode n, boolean vacio) {
                super.updateItem(n, vacio);
                setStyle(vacio || n == null ? "" : switch (n.path("estado").asText()) {
                    case "CANCELADA", "NO_ASISTIO" -> "-fx-opacity: 0.55;";
                    case "EN_CURSO" -> "-fx-background-color: #FFF4D6;";
                    case "ATENDIDA" -> "-fx-background-color: #E5F5EC;";
                    default -> "";
                });
            }
        });
        tabla.setOnMouseClicked(e -> { if (e.getClickCount() == 2) historial(); });

        Button confirmar = accion("Confirmar", "CONFIRMADA"), llego = accion("Llegó / en curso", "EN_CURSO"),
                noAsistio = accion("No asistió", "NO_ASISTIO");
        Button atendida = new Button("Marcar atendida"); atendida.setOnAction(e -> atender());
        Button cancelar = new Button("Cancelar cita"); cancelar.setOnAction(e -> cancelar());
        Button reprogramar = new Button("Reprogramar"); reprogramar.setOnAction(e -> reprogramar());
        Button historial = new Button("Historial"); historial.setOnAction(e -> historial());
        for (Button b : List.of(confirmar, llego, noAsistio, atendida, cancelar, reprogramar, historial))
            b.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        HBox acciones = new HBox(8, confirmar, llego, atendida, reprogramar, cancelar, noAsistio, historial);
        acciones.setPadding(new Insets(8, 0, 0, 0));

        mes.setHgap(4); mes.setVgap(4); mes.setPadding(new Insets(4));
        centro.getChildren().addAll(tabla, new ScrollPane(mes));
        aviso.setWrapText(true); recordatorios.getStyleClass().add("muted");
        VBox raiz = new VBox(6, recordatorios, filtros, centro, acciones, aviso);
        VBox.setVgrow(centro, Priority.ALWAYS);
        raiz.setPadding(new Insets(14));
        Scene escena = new Scene(raiz, 1180, 700); escena.getStylesheets().add(tema);
        ventana.setScene(escena);
    }

    void mostrar() {
        ventana.show();
        api.get("/api/agenda/veterinarios", lista -> {
            veterinario.getItems().setAll(api.json.nullNode());
            lista.forEach(veterinario.getItems()::add);
        }, this::error);
        api.get("/api/agenda/tipos", lista -> { tipos.clear(); lista.forEach(t -> { if (t.path("activo").asBoolean(true)) tipos.add(t); }); }, this::error);
        api.get("/api/agenda/recordatorios?horas=24", lista -> recordatorios.setText(lista.isEmpty()
                ? "Sin citas en las próximas 24 horas."
                : "Próximas 24 horas: " + lista.size() + (lista.size() == 1 ? " cita" : " citas") + " · siguiente: "
                  + lista.get(0).path("mascota").asText() + " " + fechaHora(lista.get(0).path("inicio").asText()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))), e -> {});
        cargar();
    }

    // ------------------------------------------------------------------ consulta
    private LocalDate[] rango() {
        LocalDate f = fecha.getValue() == null ? LocalDate.now() : fecha.getValue();
        return switch (vista.getValue()) {
            case "Semana" -> new LocalDate[]{f.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), f.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))};
            case "Mes" -> new LocalDate[]{f.withDayOfMonth(1), YearMonth.from(f).atEndOfMonth()};
            default -> new LocalDate[]{f, f};
        };
    }

    private void mover(int sentido) {
        LocalDate f = fecha.getValue() == null ? LocalDate.now() : fecha.getValue();
        fecha.setValue(switch (vista.getValue()) { case "Semana" -> f.plusWeeks(sentido); case "Mes" -> f.plusMonths(sentido); default -> f.plusDays(sentido); });
    }

    private void cargar() {
        LocalDate[] r = rango();
        StringBuilder ruta = new StringBuilder("/api/agenda/citas?desde=" + r[0] + "&hasta=" + r[1]);
        JsonNode vet = veterinario.getValue();
        if (vet != null && !vet.isNull()) ruta.append("&veterinarioId=").append(vet.path("id").asInt());
        if (!"Todos".equals(estado.getValue())) ruta.append("&estado=").append(estado.getValue());
        if (!buscar.getText().isBlank()) ruta.append("&q=").append(java.net.URLEncoder.encode(buscar.getText().trim(), java.nio.charset.StandardCharsets.UTF_8));
        int v = ++version;
        api.get(ruta.toString(), lista -> {
            if (v != version) return;
            List<JsonNode> citas = new ArrayList<>(); lista.forEach(citas::add);
            tabla.getItems().setAll(citas);
            boolean esMes = "Mes".equals(vista.getValue());
            tabla.setVisible(!esMes); centro.getChildren().get(1).setVisible(esMes);
            if (esMes) pintarMes(r[0], citas);
            info(citas.size() + (citas.size() == 1 ? " cita" : " citas") + " del " + r[0].format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + (r[0].equals(r[1]) ? "" : " al " + r[1].format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) + ".");
        }, this::error);
    }

    /** Calendario mensual: cada día muestra sus citas activas; al hacer clic se abre la vista del día. */
    private void pintarMes(LocalDate primero, List<JsonNode> citas) {
        mes.getChildren().clear(); mes.getColumnConstraints().clear();
        for (int i = 0; i < 7; i++) {
            ColumnConstraints c = new ColumnConstraints(); c.setPercentWidth(100.0 / 7); mes.getColumnConstraints().add(c);
            Label d = new Label(DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, MX).toUpperCase(MX)); d.getStyleClass().add("section-title");
            mes.add(d, i, 0);
        }
        int desfase = primero.getDayOfWeek().getValue() - 1;
        int dias = YearMonth.from(primero).lengthOfMonth();
        for (int dia = 1; dia <= dias; dia++) {
            LocalDate f = primero.withDayOfMonth(dia);
            VBox caja = new VBox(2); caja.setPadding(new Insets(4)); caja.setMinHeight(86);
            caja.setStyle("-fx-background-color: white; -fx-border-color: #D7E0E8; -fx-background-radius: 6; -fx-border-radius: 6;"
                    + (f.equals(LocalDate.now()) ? "-fx-border-color: #315B79; -fx-border-width: 2;" : ""));
            Label numero = new Label(String.valueOf(dia)); numero.setStyle("-fx-font-weight: bold;");
            caja.getChildren().add(numero);
            List<JsonNode> delDia = citas.stream().filter(c -> fechaHora(c.path("inicio").asText()).toLocalDate().equals(f)).toList();
            delDia.stream().limit(3).forEach(c -> {
                Label l = new Label(fechaHora(c.path("inicio").asText()).format(HORA) + " " + c.path("mascota").asText());
                l.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (c.path("estado").asText().matches("CANCELADA|NO_ASISTIO") ? "#9AA5B1" : "#17324D") + ";");
                caja.getChildren().add(l);
            });
            if (delDia.size() > 3) caja.getChildren().add(new Label("+" + (delDia.size() - 3) + " más"));
            caja.setOnMouseClicked(e -> { fecha.setValue(f); vista.setValue("Día"); });
            int celda = desfase + dia - 1;
            mes.add(caja, celda % 7, 1 + celda / 7);
        }
    }

    // ------------------------------------------------------------------ alta
    private void nuevaCita() {
        Dialog<Void> d = dialogo("Nueva cita", "Seleccione paciente, veterinario y un horario disponible");
        TextField buscarMascota = new TextField(); buscarMascota.setPromptText("Buscar mascota o dueño y presione Enter");
        ListView<JsonNode> mascotas = new ListView<>(); mascotas.setPrefHeight(110);
        mascotas.setCellFactory(v -> new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) {
            super.updateItem(n, e); setText(e || n == null ? null : n.path("mascota").asText() + " (" + n.path("especie").asText() + ") · " + n.path("dueno").asText() + " · " + n.path("telefono").asText("")); } });
        if (mascotaInicial != null) {
            ObjectNode m = api.objeto().put("mascotaId", mascotaInicial.path("id").asInt()).put("mascota", mascotaInicial.path("nombre").asText())
                    .put("especie", mascotaInicial.path("especie").asText()).put("dueno", "paciente seleccionado");
            mascotas.getItems().add(m); mascotas.getSelectionModel().select(0);
        }
        buscarMascota.setOnAction(e -> api.get("/api/clinica/expedientes?q=" + java.net.URLEncoder.encode(buscarMascota.getText().trim(), java.nio.charset.StandardCharsets.UTF_8),
                lista -> { mascotas.getItems().clear(); lista.forEach(mascotas.getItems()::add); }, this::error));
        ComboBox<JsonNode> vet = new ComboBox<>(); vet.setCellFactory(v -> celda()); vet.setButtonCell(celda());
        veterinario.getItems().stream().filter(x -> x != null && !x.isNull()).forEach(vet.getItems()::add);
        if (veterinario.getValue() != null && !veterinario.getValue().isNull()) vet.setValue(veterinario.getValue());
        ComboBox<JsonNode> tipo = new ComboBox<>(FXCollections.observableArrayList(tipos));
        tipo.setCellFactory(v -> celdaTipo()); tipo.setButtonCell(celdaTipo());
        if (!tipos.isEmpty()) tipo.setValue(tipos.get(0));
        DatePicker dia = new DatePicker(fecha.getValue() == null || fecha.getValue().isBefore(LocalDate.now()) ? LocalDate.now() : fecha.getValue());
        ComboBox<String> hora = new ComboBox<>(); hora.setPromptText("Elija veterinario, tipo y fecha");
        Runnable horarios = () -> {
            hora.getItems().clear();
            if (vet.getValue() == null || tipo.getValue() == null || dia.getValue() == null) return;
            api.get("/api/agenda/disponibilidad?veterinarioId=" + vet.getValue().path("id").asInt() + "&fecha=" + dia.getValue()
                    + "&tipoCitaId=" + tipo.getValue().path("id").asInt(), lista -> {
                lista.forEach(s -> hora.getItems().add(fechaHora(s.path("inicio").asText()).format(HORA)));
                hora.setPromptText(hora.getItems().isEmpty() ? "Sin horarios disponibles ese día" : "Seleccione hora");
            }, this::error);
        };
        vet.valueProperty().addListener((o, a, b) -> horarios.run());
        tipo.valueProperty().addListener((o, a, b) -> horarios.run());
        dia.valueProperty().addListener((o, a, b) -> horarios.run());
        horarios.run();
        TextField motivo = new TextField(); TextArea notas = new TextArea(); notas.setPrefRowCount(2); notas.setWrapText(true);
        Label error = new Label(); error.setWrapText(true); error.setStyle("-fx-text-fill: #B3261E;");
        Button guardar = new Button("Agendar"); guardar.getStyleClass().add("primary");
        GridPane g = formulario(new String[]{"Paciente", "", "Veterinario", "Tipo de cita", "Fecha", "Hora", "Motivo", "Notas"},
                buscarMascota, mascotas, vet, tipo, dia, hora, motivo, notas);
        d.getDialogPane().setContent(new VBox(10, g, error, guardar));
        guardar.setOnAction(e -> {
            JsonNode m = mascotas.getSelectionModel().getSelectedItem();
            if (m == null || vet.getValue() == null || tipo.getValue() == null || hora.getValue() == null) { error.setText("Complete paciente, veterinario, tipo y hora."); return; }
            ObjectNode cuerpo = api.objeto().put("mascotaId", m.path("mascotaId").asInt()).put("veterinarioId", vet.getValue().path("id").asInt())
                    .put("tipoCitaId", tipo.getValue().path("id").asInt()).put("inicio", dia.getValue() + "T" + hora.getValue() + ":00")
                    .put("motivo", motivo.getText().trim()).put("notas", notas.getText().trim());
            guardar.setDisable(true);
            api.post("/api/agenda/citas", cuerpo, c -> { d.close(); fecha.setValue(dia.getValue()); cargar(); info("Cita agendada para " + c.path("mascota").asText() + "."); },
                    m2 -> { guardar.setDisable(false); error.setText(m2); horarios.run(); });
        });
        d.showAndWait();
    }

    // ------------------------------------------------------------------ acciones
    private Button accion(String texto, String nuevo) {
        Button b = new Button(texto);
        b.setOnAction(e -> cambiarEstado(nuevo, null));
        return b;
    }

    private void cambiarEstado(String nuevo, String motivo) {
        JsonNode c = tabla.getSelectionModel().getSelectedItem(); if (c == null) return;
        ObjectNode cuerpo = api.objeto().put("estado", nuevo); if (motivo != null) cuerpo.put("motivo", motivo);
        api.put("/api/agenda/citas/" + c.path("id").asInt() + "/estado", cuerpo, r -> { cargar(); info("Cita de " + r.path("mascota").asText() + ": " + nuevo.replace('_', ' ') + "."); }, this::error);
    }

    private void cancelar() {
        JsonNode c = tabla.getSelectionModel().getSelectedItem(); if (c == null) return;
        TextInputDialog t = new TextInputDialog(); t.initOwner(ventana); t.setTitle("Cancelar cita"); t.setHeaderText("Motivo de la cancelación (obligatorio)");
        t.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(m -> cambiarEstado("CANCELADA", m));
    }

    /** Vincula la cita con la consulta registrada para esa mascota y la marca como atendida. */
    private void atender() {
        JsonNode c = tabla.getSelectionModel().getSelectedItem(); if (c == null) return;
        api.get("/api/clinica/mascotas/" + c.path("mascotaId").asInt() + "/consultas", consultas -> {
            if (consultas.isEmpty()) { error("Primero registre la consulta de " + c.path("mascota").asText() + " en el consultorio."); return; }
            ChoiceDialog<String> elegir = new ChoiceDialog<>();
            List<JsonNode> lista = new ArrayList<>(); consultas.forEach(lista::add);
            lista.forEach(x -> elegir.getItems().add("#" + x.path("id").asInt() + " · " + x.path("fecha").asText().replace('T', ' ').replaceFirst(":\\d\\d(\\..*)?$", "") + " · " + x.path("motivo").asText()));
            elegir.setSelectedItem(elegir.getItems().get(0));
            elegir.initOwner(ventana); elegir.setTitle("Marcar atendida"); elegir.setHeaderText("¿Con qué consulta se atendió la cita?");
            elegir.showAndWait().ifPresent(sel -> {
                int consultaId = Integer.parseInt(sel.substring(1, sel.indexOf(' ')));
                api.put("/api/agenda/citas/" + c.path("id").asInt() + "/atender", api.objeto().put("consultaId", consultaId),
                        r -> { cargar(); info("Cita atendida y vinculada a la consulta #" + consultaId + "."); }, this::error);
            });
        }, this::error);
    }

    private void reprogramar() {
        JsonNode c = tabla.getSelectionModel().getSelectedItem(); if (c == null) return;
        Dialog<Void> d = dialogo("Reprogramar cita", c.path("mascota").asText() + " · " + c.path("tipo").asText());
        DatePicker dia = new DatePicker(LocalDate.now()); ComboBox<String> hora = new ComboBox<>();
        Runnable horarios = () -> api.get("/api/agenda/disponibilidad?veterinarioId=" + c.path("veterinarioId").asInt() + "&fecha=" + dia.getValue()
                + "&tipoCitaId=" + c.path("tipoCitaId").asInt(), lista -> { hora.getItems().clear(); lista.forEach(s -> hora.getItems().add(fechaHora(s.path("inicio").asText()).format(HORA))); }, this::error);
        dia.valueProperty().addListener((o, a, b) -> horarios.run()); horarios.run();
        Label error = new Label(); error.setStyle("-fx-text-fill: #B3261E;"); error.setWrapText(true);
        Button guardar = new Button("Reprogramar"); guardar.getStyleClass().add("primary");
        guardar.setOnAction(e -> {
            if (hora.getValue() == null) { error.setText("Seleccione un horario disponible."); return; }
            guardar.setDisable(true);
            api.put("/api/agenda/citas/" + c.path("id").asInt() + "/reprogramar", api.objeto().put("inicio", dia.getValue() + "T" + hora.getValue() + ":00"),
                    r -> { d.close(); cargar(); info("Cita reprogramada."); }, m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.getDialogPane().setContent(new VBox(10, formulario(new String[]{"Fecha", "Hora"}, dia, hora), error, guardar));
        d.showAndWait();
    }

    private void historial() {
        JsonNode c = tabla.getSelectionModel().getSelectedItem(); if (c == null) return;
        api.get("/api/agenda/citas/" + c.path("id").asInt() + "/historial", lista -> {
            StringBuilder t = new StringBuilder();
            lista.forEach(h -> t.append(h.path("fecha").asText().replace('T', ' ').replaceFirst("\\..*", "")).append(" · ")
                    .append(h.path("usuario").asText()).append(" · ").append(h.path("accion").asText())
                    .append(h.path("detalle").asText("").isBlank() ? "" : " · " + h.path("detalle").asText()).append('\n'));
            Alert a = new Alert(Alert.AlertType.INFORMATION); a.initOwner(ventana); a.setTitle("Historial de la cita"); a.setHeaderText(c.path("mascota").asText() + " · " + c.path("estado").asText());
            TextArea area = new TextArea(t.length() == 0 ? "Sin movimientos." : t.toString()); area.setEditable(false); area.setWrapText(true); area.setPrefSize(560, 260);
            a.getDialogPane().setContent(area); a.showAndWait();
        }, this::error);
    }

    // ------------------------------------------------------------------ utilidades
    private Dialog<Void> dialogo(String titulo, String encabezado) {
        Dialog<Void> d = new Dialog<>(); d.initOwner(ventana); d.setTitle(titulo); d.setHeaderText(encabezado); d.setResizable(true);
        d.getDialogPane().getStylesheets().addAll(ventana.getScene().getStylesheets());
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefWidth(600);
        return d;
    }
    static GridPane formulario(String[] etiquetas, Control... campos) {
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8);
        ColumnConstraints a = new ColumnConstraints(110), b = new ColumnConstraints(); b.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(a, b);
        for (int i = 0; i < campos.length; i++) { g.add(new Label(etiquetas[i]), 0, i); campos[i].setMaxWidth(Double.MAX_VALUE); g.add(campos[i], 1, i); }
        return g;
    }
    private void columna(String titulo, int ancho, java.util.function.Function<JsonNode, String> valor) {
        TableColumn<JsonNode, String> c = new TableColumn<>(titulo); c.setPrefWidth(ancho);
        c.setCellValueFactory(v -> new SimpleStringProperty(valor.apply(v.getValue()))); tabla.getColumns().add(c);
    }
    private static ListCell<JsonNode> celda() {
        return new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e);
            setText(e ? null : n == null || n.isNull() ? "Todos los veterinarios" : n.path("nombre").asText()); } };
    }
    private static ListCell<JsonNode> celdaTipo() {
        return new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e);
            setText(e || n == null ? null : n.path("nombre").asText() + " · " + n.path("duracionMin").asInt() + " min"); } };
    }
    static LocalDateTime fechaHora(String s) {
        try { return LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s); } catch (Exception e) { return LocalDateTime.MIN; }
    }
    private void info(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #21865B;"); }
    private void error(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #B3261E;"); }
}
