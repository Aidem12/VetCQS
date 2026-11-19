package mx.buap.veterinaria.portal;

import java.time.LocalDateTime;
import java.time.LocalTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import static mx.buap.veterinaria.portal.Tablas.*;

/** Configuración de la agenda: tipos de cita con duración, horarios laborales por veterinario y ausencias. */
final class AgendaConfigPane {
    private static final String[] DIAS = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private final Api api;
    private final Stage owner;
    private final TableView<JsonNode> tipos = new TableView<>(), horarios = new TableView<>(), ausencias = new TableView<>();
    private final ComboBox<JsonNode> veterinario = new ComboBox<>();
    private final Label aviso = new Label();

    AgendaConfigPane(Api api, Stage owner) { this.api = api; this.owner = owner; }

    Node crear() {
        col(tipos, "Tipo de cita", 220, n -> n.path("nombre").asText()); col(tipos, "Duración", 90, n -> n.path("duracionMin").asText() + " min");
        col(tipos, "Color", 80, n -> n.path("color").asText("")); col(tipos, "Activo", 70, n -> n.path("activo").asBoolean() ? "Sí" : "No");
        Button nuevoTipo = new Button("Nuevo tipo"); nuevoTipo.setOnAction(e -> tipo(null));
        Button editarTipo = new Button("Editar"); editarTipo.setOnAction(e -> { JsonNode t = tipos.getSelectionModel().getSelectedItem(); if (t != null) tipo(t); });

        veterinario.setPromptText("Seleccione veterinario"); veterinario.setCellFactory(v -> celda()); veterinario.setButtonCell(celda());
        veterinario.valueProperty().addListener((o, a, b) -> cargarVet());
        col(horarios, "Día", 110, n -> DIAS[n.path("diaSemana").asInt()]); col(horarios, "Desde", 80, n -> n.path("horaInicio").asText().substring(0, 5));
        col(horarios, "Hasta", 80, n -> n.path("horaFin").asText().substring(0, 5));
        Button nuevoHorario = new Button("Agregar horario"); nuevoHorario.setOnAction(e -> horario());
        Button quitarHorario = new Button("Quitar horario"); quitarHorario.setOnAction(e -> {
            JsonNode h = horarios.getSelectionModel().getSelectedItem(); if (h == null) return;
            api.delete("/api/agenda/horarios/" + h.path("id").asInt(), r -> { cargarVet(); info("Horario eliminado."); }, this::error);
        });
        col(ausencias, "Tipo", 110, n -> n.path("tipo").asText()); col(ausencias, "Inicio", 130, n -> fechaHora(n.path("inicio")));
        col(ausencias, "Fin", 130, n -> fechaHora(n.path("fin"))); col(ausencias, "Motivo", 200, n -> n.path("motivo").asText(""));
        Button nuevaAusencia = new Button("Registrar vacaciones / ausencia"); nuevaAusencia.setOnAction(e -> ausencia());

        VBox izquierda = new VBox(8, titulo("TIPOS DE CITA"), tipos, new HBox(8, nuevoTipo, editarTipo));
        VBox derecha = new VBox(8, titulo("HORARIO DEL VETERINARIO"), veterinario, horarios, new HBox(8, nuevoHorario, quitarHorario),
                titulo("AUSENCIAS (no se pueden agendar citas)"), ausencias, nuevaAusencia);
        VBox.setVgrow(tipos, Priority.ALWAYS); VBox.setVgrow(horarios, Priority.ALWAYS); VBox.setVgrow(ausencias, Priority.ALWAYS);
        HBox cuerpo = new HBox(16, izquierda, derecha); HBox.setHgrow(izquierda, Priority.ALWAYS); HBox.setHgrow(derecha, Priority.ALWAYS);
        aviso.setWrapText(true);
        VBox raiz = new VBox(10, titulo("CONFIGURACIÓN DE LA AGENDA"), cuerpo, aviso); VBox.setVgrow(cuerpo, Priority.ALWAYS);
        raiz.getStyleClass().add("card"); raiz.setPadding(new Insets(12));
        return raiz;
    }

    void cargar() {
        api.get("/api/agenda/tipos?todos=true", l -> llenar(tipos, l), this::error);
        if (veterinario.getItems().isEmpty()) api.get("/api/agenda/veterinarios", l -> { l.forEach(veterinario.getItems()::add); if (!veterinario.getItems().isEmpty()) veterinario.setValue(veterinario.getItems().get(0)); }, this::error);
        else cargarVet();
    }

    private void cargarVet() {
        JsonNode v = veterinario.getValue(); if (v == null) return;
        int id = v.path("id").asInt();
        api.get("/api/agenda/horarios?veterinarioId=" + id, l -> llenar(horarios, l), this::error);
        api.get("/api/agenda/ausencias?veterinarioId=" + id, l -> llenar(ausencias, l), this::error);
    }

    private void tipo(JsonNode t) {
        TextField nombre = new TextField(t == null ? "" : t.path("nombre").asText());
        Spinner<Integer> duracion = new Spinner<>(5, 480, t == null ? 30 : t.path("duracionMin").asInt(), 5); duracion.setEditable(true);
        ColorPicker color = new ColorPicker(javafx.scene.paint.Color.web(t == null || t.path("color").isNull() ? "#315B79" : t.path("color").asText()));
        CheckBox activo = new CheckBox("Activo"); activo.setSelected(t == null || t.path("activo").asBoolean());
        Label error = rojo(); Button guardar = new Button("Guardar"); guardar.getStyleClass().add("primary");
        Dialog<Void> d = dialogo(owner, t == null ? "Nuevo tipo de cita" : "Editar tipo de cita",
                new VBox(10, formulario(new String[]{"Nombre", "Duración (min)", "Color", ""}, nombre, duracion, color, activo), error, guardar));
        guardar.setOnAction(e -> {
            javafx.scene.paint.Color c = color.getValue();
            String hex = String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
            ObjectNode cuerpo = api.objeto().put("nombre", nombre.getText().trim()).put("duracionMin", duracion.getValue()).put("color", hex).put("activo", activo.isSelected());
            guardar.setDisable(true);
            java.util.function.Consumer<JsonNode> ok = r -> { d.close(); cargar(); info("Tipo de cita guardado."); };
            if (t == null) api.post("/api/agenda/tipos", cuerpo, ok, m -> { guardar.setDisable(false); error.setText(m); });
            else api.put("/api/agenda/tipos/" + t.path("id").asInt(), cuerpo, ok, m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.showAndWait();
    }

    private void horario() {
        JsonNode v = veterinario.getValue(); if (v == null) { error("Seleccione un veterinario."); return; }
        ComboBox<String> dia = new ComboBox<>(FXCollections.observableArrayList(java.util.Arrays.copyOfRange(DIAS, 1, 8))); dia.setValue("Lunes");
        TextField inicio = new TextField("09:00"), fin = new TextField("14:00");
        Label error = rojo(); Button guardar = new Button("Agregar"); guardar.getStyleClass().add("primary");
        Dialog<Void> d = dialogo(owner, "Horario de " + v.path("nombre").asText(), new VBox(10, formulario(new String[]{"Día", "Desde (HH:mm)", "Hasta (HH:mm)"}, dia, inicio, fin), error, guardar));
        guardar.setOnAction(e -> {
            try { LocalTime.parse(inicio.getText().trim()); LocalTime.parse(fin.getText().trim()); } catch (Exception ex) { error.setText("Use el formato HH:mm."); return; }
            ObjectNode c = api.objeto().put("veterinarioId", v.path("id").asInt()).put("diaSemana", dia.getSelectionModel().getSelectedIndex() + 1)
                    .put("horaInicio", inicio.getText().trim()).put("horaFin", fin.getText().trim());
            guardar.setDisable(true);
            api.post("/api/agenda/horarios", c, r -> { d.close(); cargarVet(); info("Horario agregado."); }, m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.showAndWait();
    }

    private void ausencia() {
        JsonNode v = veterinario.getValue(); if (v == null) { error("Seleccione un veterinario."); return; }
        ComboBox<String> tipo = new ComboBox<>(FXCollections.observableArrayList("VACACIONES", "AUSENCIA", "DESCANSO")); tipo.setValue("VACACIONES");
        DatePicker diaInicio = new DatePicker(java.time.LocalDate.now()), diaFin = new DatePicker(java.time.LocalDate.now());
        TextField horaInicio = new TextField("00:00"), horaFin = new TextField("23:59"), motivo = new TextField();
        Label error = rojo(); Button guardar = new Button("Registrar"); guardar.getStyleClass().add("primary");
        Dialog<Void> d = dialogo(owner, "Ausencia de " + v.path("nombre").asText(), new VBox(10,
                formulario(new String[]{"Tipo", "Desde (día)", "Desde (hora)", "Hasta (día)", "Hasta (hora)", "Motivo"}, tipo, diaInicio, horaInicio, diaFin, horaFin, motivo), error, guardar));
        guardar.setOnAction(e -> {
            LocalDateTime ini, fin;
            try { ini = diaInicio.getValue().atTime(LocalTime.parse(horaInicio.getText().trim())); fin = diaFin.getValue().atTime(LocalTime.parse(horaFin.getText().trim())); }
            catch (Exception ex) { error.setText("Revise fechas y horas (HH:mm)."); return; }
            ObjectNode c = api.objeto().put("veterinarioId", v.path("id").asInt()).put("tipo", tipo.getValue())
                    .put("inicio", ini.toString()).put("fin", fin.toString()).put("motivo", motivo.getText().trim());
            guardar.setDisable(true);
            api.post("/api/agenda/ausencias", c, r -> { d.close(); cargarVet(); info("Ausencia registrada."); }, m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.showAndWait();
    }

    private static ListCell<JsonNode> celda() {
        return new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e); setText(e || n == null ? null : n.path("nombre").asText()); } };
    }
    private void info(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #21865B;"); }
    private void error(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #B3261E;"); }
}
