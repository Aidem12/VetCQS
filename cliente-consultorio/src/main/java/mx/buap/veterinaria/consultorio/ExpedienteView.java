package mx.buap.veterinaria.consultorio;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Expediente clínico completo de una mascota: ficha y alertas, vacunas, desparasitaciones, antecedentes,
 * consultas con notas de evolución, recetas (PDF), estudios y documentos adjuntos (PDF/PNG/JPEG hasta 5 MB).
 * Nada se borra: los registros erróneos se anulan con motivo y quedan en la bitácora.
 */
final class ExpedienteView {
    private final Api api;
    private final Stage ventana = new Stage();
    private final int mascotaId;
    private final Label ficha = new Label();
    private final VBox alertas = new VBox(4);
    private final Label aviso = new Label();
    private final TableView<JsonNode> vacunas = new TableView<>(), preventivos = new TableView<>(), antecedentes = new TableView<>(),
            consultas = new TableView<>(), recetas = new TableView<>(), estudios = new TableView<>(), documentos = new TableView<>();
    private final TextArea detalleConsulta = new TextArea();
    private JsonNode datos;

    ExpedienteView(Api api, Stage owner, int mascotaId, String nombre, String tema) {
        this.api = api; this.mascotaId = mascotaId;
        ventana.initOwner(owner); ventana.setTitle("Expediente clínico · " + nombre);
        ficha.setWrapText(true);
        Button pdf = new Button("Historial clínico en PDF"); pdf.setOnAction(e -> guardarPdf("/api/clinica/expedientes/" + mascotaId + "/pdf", "historial-" + nombre + ".pdf"));
        Button actualizar = new Button("Actualizar"); actualizar.setOnAction(e -> cargar());
        VBox resumen = new VBox(10, titulo("FICHA DEL PACIENTE"), ficha, titulo("ALERTAS CLÍNICAS"), alertas, new HBox(8, pdf, actualizar));
        resumen.setPadding(new Insets(12));

        col(vacunas, "Vacuna", 150, n -> n.path("vacuna").asText()); col(vacunas, "Dosis", 70, n -> n.path("dosis").asText(""));
        col(vacunas, "Lote", 80, n -> n.path("lote").asText("")); col(vacunas, "Aplicada", 90, n -> n.path("fechaAplicacion").asText());
        col(vacunas, "Próximo refuerzo", 110, n -> n.path("proximoRefuerzo").asText("")); col(vacunas, "Veterinario", 120, n -> n.path("veterinario").asText(""));
        col(vacunas, "Estado", 160, ExpedienteView::estadoAnulable);
        col(preventivos, "Tipo", 150, n -> n.path("tipo").asText().replace('_', ' ')); col(preventivos, "Producto", 140, n -> n.path("producto").asText());
        col(preventivos, "Dosis", 80, n -> n.path("dosis").asText("")); col(preventivos, "Aplicada", 90, n -> n.path("fechaAplicacion").asText());
        col(preventivos, "Próxima", 90, n -> n.path("proximaAplicacion").asText("")); col(preventivos, "Estado", 160, ExpedienteView::estadoAnulable);
        Button nuevaVacuna = new Button("Registrar vacuna"); nuevaVacuna.getStyleClass().add("primary"); nuevaVacuna.setOnAction(e -> vacuna());
        Button anularVacuna = new Button("Anular vacuna"); anularVacuna.setOnAction(e -> anular("vacunas", vacunas));
        Button nuevoPrev = new Button("Registrar desparasitación"); nuevoPrev.setOnAction(e -> preventivo());
        Button anularPrev = new Button("Anular"); anularPrev.setOnAction(e -> anular("preventivos", preventivos));
        VBox tabVacunas = new VBox(8, titulo("VACUNACIÓN"), vacunas, new HBox(8, nuevaVacuna, anularVacuna),
                titulo("DESPARASITACIÓN Y PREVENTIVOS"), preventivos, new HBox(8, nuevoPrev, anularPrev));

        col(antecedentes, "Tipo", 110, n -> n.path("tipo").asText()); col(antecedentes, "Descripción", 320, n -> n.path("descripcion").asText());
        col(antecedentes, "Severidad", 90, n -> n.path("severidad").asText("")); col(antecedentes, "Vigente", 70, n -> n.path("vigente").asBoolean() ? "Sí" : "No");
        col(antecedentes, "Registró", 130, n -> n.path("registradoPor").asText(""));
        Button nuevoAnt = new Button("Registrar antecedente / alergia"); nuevoAnt.getStyleClass().add("primary"); nuevoAnt.setOnAction(e -> antecedente());
        Button cerrarAnt = new Button("Marcar como no vigente"); cerrarAnt.setOnAction(e -> cerrarAntecedente());
        VBox tabAnt = new VBox(8, antecedentes, new HBox(8, nuevoAnt, cerrarAnt));

        col(consultas, "Fecha", 130, n -> n.path("fecha").asText().replace('T', ' ').replaceFirst(":\\d\\d(\\..*)?$", ""));
        col(consultas, "Motivo", 180, n -> n.path("motivo").asText()); col(consultas, "Diagnóstico", 220, n -> n.path("diagnostico").asText(""));
        col(consultas, "Veterinario", 120, n -> n.path("veterinario").asText(""));
        detalleConsulta.setEditable(false); detalleConsulta.setWrapText(true); detalleConsulta.setPrefRowCount(8);
        consultas.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> detalleConsulta.setText(c == null ? "" : detalle(c)));
        Button nota = new Button("Agregar nota de evolución"); nota.setOnAction(e -> nota());
        VBox tabConsultas = new VBox(8, consultas, detalleConsulta, nota);

        col(recetas, "Folio", 90, n -> n.path("folio").asText()); col(recetas, "Fecha", 130, n -> n.path("fecha").asText().replace('T', ' ').replaceFirst(":\\d\\d(\\..*)?$", ""));
        col(recetas, "Medicamentos", 380, n -> { List<String> m = new ArrayList<>(); n.path("medicamentos").forEach(x -> m.add(x.path("medicamento").asText())); return String.join(", ", m); });
        col(recetas, "Veterinario", 130, n -> n.path("veterinario").asText(""));
        Button nuevaReceta = new Button("Nueva receta"); nuevaReceta.getStyleClass().add("primary"); nuevaReceta.setOnAction(e -> receta());
        Button pdfReceta = new Button("Receta en PDF"); pdfReceta.setOnAction(e -> {
            JsonNode r = recetas.getSelectionModel().getSelectedItem();
            if (r == null) { error("Seleccione una receta."); return; }
            guardarPdf("/api/clinica/expedientes/recetas/" + r.path("id").asInt() + "/pdf", "receta-" + r.path("folio").asText() + ".pdf");
        });
        VBox tabRecetas = new VBox(8, recetas, new HBox(8, nuevaReceta, pdfReceta));

        col(estudios, "Fecha", 90, n -> n.path("fecha").asText()); col(estudios, "Tipo", 120, n -> n.path("tipo").asText());
        col(estudios, "Descripción", 200, n -> n.path("descripcion").asText("")); col(estudios, "Resultado", 280, n -> n.path("resultado").asText(""));
        col(documentos, "Archivo", 240, n -> n.path("nombre").asText()); col(documentos, "Tipo", 110, n -> n.path("tipoMime").asText());
        col(documentos, "Tamaño", 80, n -> String.format("%.1f KB", n.path("tamanoBytes").asInt() / 1024.0));
        col(documentos, "Subido", 130, n -> n.path("creadoEn").asText().replace('T', ' ').replaceFirst(":\\d\\d(\\..*)?$", ""));
        col(documentos, "Por", 120, n -> n.path("subidoPor").asText(""));
        Button nuevoEstudio = new Button("Registrar estudio / resultado"); nuevoEstudio.setOnAction(e -> estudio());
        Button adjuntar = new Button("Adjuntar archivo"); adjuntar.getStyleClass().add("primary"); adjuntar.setOnAction(e -> adjuntar());
        Button descargar = new Button("Descargar"); descargar.setOnAction(e -> descargar());
        VBox tabEstudios = new VBox(8, titulo("ESTUDIOS Y RESULTADOS"), estudios, nuevoEstudio,
                titulo("DOCUMENTOS (PDF, PNG o JPEG · máx. 5 MB)"), documentos, new HBox(8, adjuntar, descargar));

        TabPane tabs = new TabPane(new Tab("Resumen", resumen), new Tab("Vacunas", pad(tabVacunas)), new Tab("Antecedentes", pad(tabAnt)),
                new Tab("Consultas", pad(tabConsultas)), new Tab("Recetas", pad(tabRecetas)), new Tab("Estudios y archivos", pad(tabEstudios)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        aviso.setWrapText(true);
        VBox raiz = new VBox(8, tabs, aviso); VBox.setVgrow(tabs, Priority.ALWAYS); raiz.setPadding(new Insets(10));
        Scene escena = new Scene(raiz, 1020, 680); escena.getStylesheets().add(tema); ventana.setScene(escena);
    }

    void mostrar() { ventana.show(); cargar(); }

    private void cargar() {
        api.get("/api/clinica/expedientes/" + mascotaId, f -> {
            datos = f;
            JsonNode m = f.path("mascota");
            ficha.setText(m.path("nombre").asText() + " · " + m.path("especie").asText() + (m.path("raza").asText("").isBlank() ? "" : " · " + m.path("raza").asText())
                    + "\nSexo: " + m.path("sexo").asText("") + " · Edad: " + m.path("edad").asText("sin dato") + " · Peso: " + m.path("pesoKg").asText("—") + " kg"
                    + "\nIdentificación/microchip: " + m.path("identificacion").asText("—") + " · Color: " + m.path("color").asText("—")
                    + " · Esterilizado: " + (m.path("esterilizado").isNull() ? "sin dato" : m.path("esterilizado").asBoolean() ? "sí" : "no")
                    + "\nDueño: " + m.path("dueno").asText() + " · Tel. " + m.path("telefono").asText("—") + " · " + m.path("correo").asText("")
                    + (m.path("observaciones").asText("").isBlank() ? "" : "\nObservaciones: " + m.path("observaciones").asText()));
            alertas.getChildren().clear();
            f.path("alertas").forEach(a -> {
                Label l = new Label("⚠ " + a.path("tipo").asText() + (a.path("severidad").isNull() ? "" : " (" + a.path("severidad").asText() + ")") + ": " + a.path("descripcion").asText());
                l.setWrapText(true); l.setStyle("-fx-text-fill: #B3261E; -fx-font-weight: bold;"); alertas.getChildren().add(l);
            });
            if (alertas.getChildren().isEmpty()) alertas.getChildren().add(new Label("Sin alergias ni alertas vigentes."));
            llenar(vacunas, f.path("vacunas")); llenar(preventivos, f.path("preventivos")); llenar(antecedentes, f.path("antecedentes"));
            llenar(consultas, f.path("consultas")); llenar(recetas, f.path("recetas")); llenar(estudios, f.path("estudios")); llenar(documentos, f.path("documentos"));
        }, this::error);
    }

    // ------------------------------------------------------------------ altas
    private void vacuna() {
        TextField nombre = new TextField(), lote = new TextField(), dosis = new TextField(), obs = new TextField();
        DatePicker aplicada = new DatePicker(LocalDate.now()), refuerzo = new DatePicker(LocalDate.now().plusYears(1));
        formulario("Registrar vacuna", new String[]{"Vacuna", "Lote", "Dosis", "Fecha de aplicación", "Próximo refuerzo", "Observaciones"},
                new Control[]{nombre, lote, dosis, aplicada, refuerzo, obs}, () -> {
                    ObjectNode c = api.objeto().put("vacuna", nombre.getText().trim()).put("lote", lote.getText().trim()).put("dosis", dosis.getText().trim())
                            .put("observaciones", obs.getText().trim());
                    fecha(c, "fechaAplicacion", aplicada); fecha(c, "proximoRefuerzo", refuerzo);
                    return new Object[]{"/api/clinica/expedientes/" + mascotaId + "/vacunas", c};
                });
    }

    private void preventivo() {
        ComboBox<String> tipo = new ComboBox<>(FXCollections.observableArrayList("DESPARASITACION_INTERNA", "DESPARASITACION_EXTERNA", "OTRO")); tipo.setValue("DESPARASITACION_INTERNA");
        TextField producto = new TextField(), dosis = new TextField(), obs = new TextField();
        DatePicker aplicada = new DatePicker(LocalDate.now()), proxima = new DatePicker(LocalDate.now().plusMonths(3));
        formulario("Registrar desparasitación", new String[]{"Tipo", "Producto", "Dosis", "Aplicada", "Próxima aplicación", "Observaciones"},
                new Control[]{tipo, producto, dosis, aplicada, proxima, obs}, () -> {
                    ObjectNode c = api.objeto().put("tipo", tipo.getValue()).put("producto", producto.getText().trim()).put("dosis", dosis.getText().trim()).put("observaciones", obs.getText().trim());
                    fecha(c, "fechaAplicacion", aplicada); fecha(c, "proximaAplicacion", proxima);
                    return new Object[]{"/api/clinica/expedientes/" + mascotaId + "/preventivos", c};
                });
    }

    private void antecedente() {
        ComboBox<String> tipo = new ComboBox<>(FXCollections.observableArrayList("ALERGIA", "ENFERMEDAD", "CIRUGIA", "ANTECEDENTE", "ALERTA")); tipo.setValue("ALERGIA");
        ComboBox<String> severidad = new ComboBox<>(FXCollections.observableArrayList("LEVE", "MODERADA", "GRAVE"));
        TextField descripcion = new TextField();
        formulario("Registrar antecedente", new String[]{"Tipo", "Descripción", "Severidad"}, new Control[]{tipo, descripcion, severidad}, () -> {
            ObjectNode c = api.objeto().put("tipo", tipo.getValue()).put("descripcion", descripcion.getText().trim());
            if (severidad.getValue() != null) c.put("severidad", severidad.getValue());
            return new Object[]{"/api/clinica/expedientes/" + mascotaId + "/antecedentes", c};
        });
    }

    private void estudio() {
        TextField tipo = new TextField(), descripcion = new TextField(); TextArea resultado = new TextArea(); resultado.setPrefRowCount(5); resultado.setWrapText(true);
        DatePicker f = new DatePicker(LocalDate.now());
        ComboBox<String> consulta = consultasCombo();
        formulario("Registrar estudio", new String[]{"Tipo (ej. Biometría)", "Descripción", "Resultado", "Fecha", "Consulta"},
                new Control[]{tipo, descripcion, resultado, f, consulta}, () -> {
                    ObjectNode c = api.objeto().put("tipo", tipo.getText().trim()).put("descripcion", descripcion.getText().trim()).put("resultado", resultado.getText().trim());
                    fecha(c, "fecha", f); consultaId(c, consulta);
                    return new Object[]{"/api/clinica/expedientes/" + mascotaId + "/estudios", c};
                });
    }

    private void receta() {
        Dialog<Void> d = dialogo("Nueva receta");
        GridPane renglones = new GridPane(); renglones.setHgap(6); renglones.setVgap(6);
        String[] cab = {"Medicamento", "Dosis", "Frecuencia", "Vía", "Duración", "Indicaciones"};
        for (int i = 0; i < cab.length; i++) renglones.add(new Label(cab[i]), i, 0);
        List<TextField[]> filas = new ArrayList<>();
        Runnable agregar = () -> {
            TextField[] f = new TextField[6];
            for (int i = 0; i < 6; i++) { f[i] = new TextField(); f[i].setPrefColumnCount(i == 0 || i == 5 ? 12 : 7); renglones.add(f[i], i, filas.size() + 1); }
            f[3].setText("Oral"); filas.add(f);
        };
        agregar.run();
        Button otro = new Button("+ Agregar medicamento"); otro.setOnAction(e -> agregar.run());
        TextArea indicaciones = new TextArea(); indicaciones.setPromptText("Indicaciones generales"); indicaciones.setPrefRowCount(3); indicaciones.setWrapText(true);
        ComboBox<String> consulta = consultasCombo();
        Label error = rojo(); Button guardar = new Button("Guardar receta"); guardar.getStyleClass().add("primary");
        guardar.setOnAction(e -> {
            ArrayNode meds = api.json.createArrayNode();
            for (TextField[] f : filas) {
                if (f[0].getText().isBlank() && f[1].getText().isBlank()) continue;
                meds.addObject().put("medicamento", f[0].getText().trim()).put("dosis", f[1].getText().trim()).put("frecuencia", f[2].getText().trim())
                        .put("via", f[3].getText().trim()).put("duracion", f[4].getText().trim()).put("indicaciones", f[5].getText().trim());
            }
            if (meds.isEmpty()) { error.setText("Agregue al menos un medicamento."); return; }
            ObjectNode c = api.objeto().put("indicaciones", indicaciones.getText().trim()); c.set("medicamentos", meds); consultaId(c, consulta);
            guardar.setDisable(true);
            api.post("/api/clinica/expedientes/" + mascotaId + "/recetas", c, r -> { d.close(); cargar(); info("Receta " + r.path("folio").asText() + " guardada. Puede generar el PDF."); },
                    m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.getDialogPane().setContent(new VBox(10, new ScrollPane(renglones), otro, indicaciones, new HBox(8, new Label("Consulta"), consulta), error, guardar));
        d.getDialogPane().setPrefWidth(900); d.showAndWait();
    }

    private void nota() {
        JsonNode c = consultas.getSelectionModel().getSelectedItem();
        if (c == null) { error("Seleccione la consulta a la que se agrega la nota."); return; }
        TextArea texto = new TextArea(); texto.setWrapText(true); texto.setPrefRowCount(6);
        formulario("Nota de evolución", new String[]{"Nota"}, new Control[]{texto},
                () -> new Object[]{"/api/clinica/expedientes/consultas/" + c.path("id").asInt() + "/notas", api.objeto().put("texto", texto.getText().trim())});
    }

    // ------------------------------------------------------------------ acciones
    private void anular(String tipo, TableView<JsonNode> tabla) {
        JsonNode n = tabla.getSelectionModel().getSelectedItem();
        if (n == null) { error("Seleccione el registro a anular."); return; }
        TextInputDialog t = new TextInputDialog(); t.initOwner(ventana); t.setTitle("Anular registro");
        t.setHeaderText("El registro no se borra: queda marcado como anulado.\nEscriba el motivo:");
        t.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(m ->
                api.put("/api/clinica/expedientes/" + tipo + "/" + n.path("id").asInt() + "/anular", api.objeto().put("motivo", m), r -> { cargar(); info("Registro anulado."); }, this::error));
    }

    private void cerrarAntecedente() {
        JsonNode n = antecedentes.getSelectionModel().getSelectedItem();
        if (n == null) { error("Seleccione un antecedente."); return; }
        api.put("/api/clinica/expedientes/antecedentes/" + n.path("id").asInt() + "/cerrar", api.objeto(), r -> { cargar(); info("Antecedente marcado como no vigente."); }, this::error);
    }

    private void adjuntar() {
        FileChooser fc = new FileChooser(); fc.setTitle("Adjuntar al expediente");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF o imagen", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(ventana); if (f == null) return;
        if (f.length() > 5L * 1024 * 1024) { error("El archivo supera 5 MB."); return; }
        api.subir("/api/clinica/expedientes/" + mascotaId + "/documentos", f.toPath(), r -> { cargar(); info("Archivo adjuntado."); }, this::error);
    }

    private void descargar() {
        JsonNode n = documentos.getSelectionModel().getSelectedItem();
        if (n == null) { error("Seleccione un documento."); return; }
        FileChooser fc = new FileChooser(); fc.setInitialFileName(n.path("nombre").asText());
        File f = fc.showSaveDialog(ventana); if (f == null) return;
        api.descargar("/api/clinica/expedientes/documentos/" + n.path("id").asInt(), f.toPath(), () -> info("Guardado en " + f.getAbsolutePath()), this::error);
    }

    private void guardarPdf(String ruta, String nombre) {
        FileChooser fc = new FileChooser(); fc.setInitialFileName(nombre.replaceAll("[\\\\/:*?\"<>|]", "_"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File f = fc.showSaveDialog(ventana); if (f == null) return;
        api.descargar(ruta, f.toPath(), () -> { info("PDF guardado en " + f.getAbsolutePath()); abrir(f); }, this::error);
    }

    private static void abrir(File f) {
        try { if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(f); } catch (Throwable ignorada) { /* el archivo ya quedó guardado */ }
    }

    // ------------------------------------------------------------------ utilidades
    private interface Cuerpo { Object[] crear(); }

    private void formulario(String titulo, String[] etiquetas, Control[] campos, Cuerpo cuerpo) {
        Dialog<Void> d = dialogo(titulo);
        Label error = rojo(); Button guardar = new Button("Guardar"); guardar.getStyleClass().add("primary");
        guardar.setOnAction(e -> {
            Object[] r = cuerpo.crear();
            guardar.setDisable(true);
            api.post((String) r[0], (JsonNode) r[1], ok -> { d.close(); cargar(); info(titulo + ": guardado."); }, m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.getDialogPane().setContent(new VBox(10, AgendaView.formulario(etiquetas, campos), error, guardar));
        d.showAndWait();
    }

    private Dialog<Void> dialogo(String titulo) {
        Dialog<Void> d = new Dialog<>(); d.initOwner(ventana); d.setTitle(titulo); d.setHeaderText(titulo); d.setResizable(true);
        d.getDialogPane().getStylesheets().addAll(ventana.getScene().getStylesheets());
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefWidth(560);
        return d;
    }

    private ComboBox<String> consultasCombo() {
        ComboBox<String> c = new ComboBox<>(); c.getItems().add("Sin consulta");
        if (datos != null) datos.path("consultas").forEach(x -> c.getItems().add("#" + x.path("id").asInt() + " · " + x.path("fecha").asText().substring(0, 10) + " · " + x.path("motivo").asText()));
        c.setValue(c.getItems().size() > 1 ? c.getItems().get(1) : "Sin consulta");
        return c;
    }
    private static void consultaId(ObjectNode c, ComboBox<String> combo) {
        String v = combo.getValue();
        if (v != null && v.startsWith("#")) c.put("consultaId", Integer.parseInt(v.substring(1, v.indexOf(' '))));
    }
    private static void fecha(ObjectNode c, String campo, DatePicker p) { if (p.getValue() != null) c.put(campo, p.getValue().toString()); }

    private static String detalle(JsonNode c) {
        StringBuilder t = new StringBuilder("Motivo: ").append(c.path("motivo").asText())
                .append("\nSignos: peso ").append(c.path("pesoKg").asText("—")).append(" kg · temp. ").append(c.path("temperaturaC").asText("—"))
                .append(" °C · FC ").append(c.path("frecuenciaCardiaca").asText("—")).append(" lpm · FR ").append(c.path("frecuenciaRespiratoria").asText("—"))
                .append(" rpm · condición corporal ").append(c.path("condicionCorporal").asText("—"))
                .append("\nDiagnóstico: ").append(c.path("diagnostico").asText("")).append("\nTratamiento: ").append(c.path("tratamiento").asText(""));
        if (!c.path("evolucion").asText("").isBlank()) t.append("\nEvolución: ").append(c.path("evolucion").asText());
        c.path("notas").forEach(n -> t.append("\n— Nota ").append(n.path("fecha").asText().replace('T', ' ').replaceFirst(":\\d\\d(\\..*)?$", ""))
                .append(" (").append(n.path("autor").asText()).append("): ").append(n.path("texto").asText()));
        return t.toString();
    }
    private static String estadoAnulable(JsonNode n) { return n.path("anulada").asBoolean() ? "ANULADA: " + n.path("motivoAnulacion").asText("") : "Vigente"; }
    private static void llenar(TableView<JsonNode> t, JsonNode lista) { List<JsonNode> l = new ArrayList<>(); lista.forEach(l::add); t.getItems().setAll(l); }
    private static void col(TableView<JsonNode> t, String titulo, int ancho, Function<JsonNode, String> v) {
        TableColumn<JsonNode, String> c = new TableColumn<>(titulo); c.setPrefWidth(ancho);
        c.setCellValueFactory(x -> new SimpleStringProperty(v.apply(x.getValue()))); t.getColumns().add(c);
        t.setPlaceholder(new Label("Sin registros."));
    }
    private static Label titulo(String t) { Label l = new Label(t); l.getStyleClass().add("section-title"); return l; }
    private static Label rojo() { Label l = new Label(); l.setWrapText(true); l.setStyle("-fx-text-fill: #B3261E;"); return l; }
    private static VBox pad(VBox v) { v.setPadding(new Insets(12)); v.getChildren().stream().filter(n -> n instanceof TableView).forEach(n -> VBox.setVgrow(n, Priority.ALWAYS)); return v; }
    private void info(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #21865B;"); }
    private void error(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #B3261E;"); }
}
