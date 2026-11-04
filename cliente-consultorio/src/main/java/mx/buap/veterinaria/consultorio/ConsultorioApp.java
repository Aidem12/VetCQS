package mx.buap.veterinaria.consultorio;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class ConsultorioApp extends Application {
    static final String SERVIDOR = System.getProperty("servidor", "http://localhost:8080");
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Label estado = new Label("Servidor: verificando...");
    private final ListView<JsonNode> duenos = new ListView<>();
    private final ListView<JsonNode> mascotas = new ListView<>();
    private final ListView<JsonNode> historial = new ListView<>();
    private final TextField buscar = new TextField();
    private final TextField motivo = new TextField();
    private final TextField peso = new TextField();
    private final TextField temperatura = new TextField();
    private final TextArea diagnostico = new TextArea();
    private final TextArea tratamiento = new TextArea();
    private final TextField productoId = new TextField();
    private final TextField cantidad = new TextField("1");
    private Stage ventanaPrincipal;
    private JsonNode duenoSeleccionado;
    private JsonNode mascotaSeleccionada;
    private JsonNode consultaSeleccionada;

    public ConsultorioApp() {
        java.net.CookieManager cookies = new java.net.CookieManager(
                null, java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        String sesion = System.getProperty("sesion", "");
        if (!sesion.isBlank()) {
            try {
                cookies.getCookieStore().add(URI.create(SERVIDOR),
                        java.net.HttpCookie.parse(sesion).get(0));
            } catch (IllegalArgumentException ignored) {
                // El servidor rechazará la sesión inválida con 401/403.
            }
        }
        http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    @Override
    public void start(Stage ventana) {
        ventanaPrincipal = ventana;
        Label marca = new Label("SISTEMA VETERINARIA"); marca.getStyleClass().add("brand");
        Label subtitulo = new Label("Atención médica  /  Consultorio"); subtitulo.getStyleClass().add("subtitle");
        estado.getStyleClass().add("badge");
        HBox barra = new HBox(18, new VBox(2, marca, subtitulo), espacio(), estado);
        barra.setAlignment(javafx.geometry.Pos.CENTER_LEFT); barra.getStyleClass().add("header");

        buscar.setPromptText("Buscar dueño por nombre o teléfono");
        Button buscarBtn = new Button("Buscar");
        buscarBtn.setMinWidth(Region.USE_PREF_SIZE);
        buscar.setMinWidth(0);
        buscarBtn.setOnAction(e -> buscarDuenos());
        buscar.setOnAction(e -> buscarDuenos());
        VBox panelDuenos = panel("DUEÑOS", new HBox(8, buscar, buscarBtn), duenos);
        VBox panelMascotas = panel("MASCOTAS", mascotas);
        VBox panelHistorial = panel("HISTORIAL CLÍNICO", historial);
        duenos.setCellFactory(v -> textoCelda(n -> n.path("nombre").asText()
                + " · " + n.path("telefono").asText("sin teléfono")));
        mascotas.setCellFactory(v -> textoCelda(n -> n.path("nombre").asText()
                + " · " + n.path("especie").asText()));
        historial.setCellFactory(v -> textoCelda(n -> n.path("fecha").asText()
                + " · " + n.path("motivo").asText()));
        duenos.setOnMouseClicked(e -> seleccionarDueno());
        mascotas.setOnMouseClicked(e -> seleccionarMascota());
        historial.setOnMouseClicked(e -> {
            consultaSeleccionada = historial.getSelectionModel().getSelectedItem();
        });

        GridPane formulario = new GridPane();
        formulario.setHgap(10);
        formulario.setVgap(8);
        formulario.setPadding(new Insets(16));
        formulario.addRow(0, new Label("Motivo"), motivo);
        formulario.addRow(1, new Label("Peso (kg)"), peso);
        formulario.addRow(2, new Label("Temperatura (°C)"), temperatura);
        formulario.addRow(3, new Label("Diagnóstico"), diagnostico);
        formulario.addRow(4, new Label("Tratamiento"), tratamiento);
        diagnostico.setPrefRowCount(3);
        tratamiento.setPrefRowCount(3);
        ColumnConstraints etiqueta = new ColumnConstraints(110);
        ColumnConstraints campo = new ColumnConstraints();
        campo.setHgrow(Priority.ALWAYS);
        formulario.getColumnConstraints().addAll(etiqueta, campo);
        Button guardarConsulta = new Button("Guardar consulta");
        guardarConsulta.setOnAction(e -> guardarConsulta());
        formulario.add(guardarConsulta, 1, 5);
        guardarConsulta.getStyleClass().add("primary");
        guardarConsulta.setMaxWidth(Double.MAX_VALUE);

        GridPane precuenta = new GridPane();
        precuenta.setHgap(8);
        precuenta.setVgap(8);
        precuenta.setPadding(new Insets(16));
        productoId.setPromptText("ID producto o servicio");
        Button agregar = new Button("Agregar");
        ListView<String> detalles = new ListView<>();
        Button enviar = new Button("Enviar precuenta a caja");
        agregar.setOnAction(e -> {
            if (!productoId.getText().isBlank()) {
                detalles.getItems().add(productoId.getText().trim() + " x " + cantidad.getText().trim());
                productoId.clear();
            }
        });
        enviar.setOnAction(e -> enviarPrecuenta(detalles));
        productoId.setMaxWidth(Double.MAX_VALUE); cantidad.setPrefColumnCount(4);
        precuenta.add(new Label("Producto o servicio"), 0, 0, 2, 1);
        precuenta.add(productoId, 0, 1, 2, 1);
        precuenta.addRow(2, new Label("Cantidad"), cantidad);
        precuenta.add(agregar, 0, 3, 2, 1);
        precuenta.add(detalles, 0, 4, 2, 1);
        precuenta.add(enviar, 0, 5, 2, 1);
        detalles.setPrefHeight(140);
        detalles.setMinHeight(100);
        historial.setMinHeight(100);
        GridPane.setHgrow(productoId, Priority.ALWAYS); GridPane.setHgrow(cantidad, Priority.ALWAYS);
        ColumnConstraints first = new ColumnConstraints(); first.setPercentWidth(50);
        ColumnConstraints second = new ColumnConstraints(); second.setPercentWidth(50);
        precuenta.getColumnConstraints().addAll(first, second);
        enviar.getStyleClass().add("primary"); enviar.setMaxWidth(Double.MAX_VALUE);
        formulario.setPadding(Insets.EMPTY); precuenta.setPadding(Insets.EMPTY);
        VBox consulta = panel("CONSULTA MÉDICA", formulario);
        VBox cuenta = panel("PRECUENTA", precuenta);
        cuenta.setMinHeight(Region.USE_PREF_SIZE);
        VBox izquierda = new VBox(14, panelDuenos, panelMascotas); izquierda.setPrefWidth(300);
        VBox derecha = new VBox(14, panelHistorial, cuenta); derecha.setPrefWidth(340);
        consulta.setPrefWidth(420);
        HBox cuerpo = new HBox(14, izquierda, consulta, derecha); cuerpo.setPadding(new Insets(18));
        HBox.setHgrow(izquierda, Priority.ALWAYS); HBox.setHgrow(consulta, Priority.ALWAYS); HBox.setHgrow(derecha, Priority.ALWAYS);
        HBox.setHgrow(buscar, Priority.ALWAYS); buscar.setPrefColumnCount(12);
        VBox.setVgrow(panelHistorial, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(cuerpo); scroll.setFitToWidth(true); scroll.setFitToHeight(true);
        BorderPane raiz = new BorderPane(scroll, barra, null, null, null);
        ventana.setTitle("Sistema Veterinaria · Consultorio");
        Scene escena = new Scene(raiz, 1280, 820);
        escena.getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        ventana.setMinWidth(1050); ventana.setMinHeight(700);
        ventana.setScene(escena);
        ventana.show();
        verificarServidor();
    }

    private VBox panel(String titulo, javafx.scene.Node... nodos) {
        Label encabezado = new Label(titulo);
        encabezado.getStyleClass().add("section-title");
        VBox panel = new VBox(12);
        panel.getStyleClass().add("card");
        panel.getChildren().add(encabezado); panel.getChildren().addAll(nodos);
        for (javafx.scene.Node nodo : nodos) {
            if (nodo instanceof ListView<?>) { VBox.setVgrow(nodo, Priority.ALWAYS); }
        }
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private ListCell<JsonNode> textoCelda(java.util.function.Function<JsonNode, String> texto) {
        return new ListCell<>() {
            @Override protected void updateItem(JsonNode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : texto.apply(item));
            }
        };
    }

    private void buscarDuenos() {
        request("GET", "/api/clinica/duenos?q=" + encode(buscar.getText()), null)
                .thenAccept(r -> Platform.runLater(() -> {
                    if (r.statusCode() == 200) {
                        duenos.setItems(FXCollections.observableArrayList(parseArray(r.body())));
                    } else mostrarError("No se pudieron consultar los dueños.");
                }));
    }

    private void seleccionarDueno() {
        duenoSeleccionado = duenos.getSelectionModel().getSelectedItem();
        if (duenoSeleccionado == null) return;
        request("GET", "/api/clinica/duenos/" + duenoSeleccionado.path("id").asInt() + "/mascotas", null)
                .thenAccept(r -> Platform.runLater(() ->
                        mascotas.setItems(FXCollections.observableArrayList(parseArray(r.body())))));
    }

    private void seleccionarMascota() {
        mascotaSeleccionada = mascotas.getSelectionModel().getSelectedItem();
        if (mascotaSeleccionada == null) return;
        request("GET", "/api/clinica/mascotas/" + mascotaSeleccionada.path("id").asInt() + "/consultas", null)
                .thenAccept(r -> Platform.runLater(() ->
                        historial.setItems(FXCollections.observableArrayList(parseArray(r.body())))));
    }

    private void guardarConsulta() {
        if (mascotaSeleccionada == null) { mostrarError("Seleccione una mascota."); return; }
        String cuerpo = "{\"mascotaId\":" + mascotaSeleccionada.path("id").asInt()
                + ",\"veterinarioId\":2,\"motivo\":\"" + escapar(motivo.getText())
                + "\",\"pesoKg\":" + numero(peso) + ",\"temperaturaC\":" + numero(temperatura)
                + ",\"diagnostico\":\"" + escapar(diagnostico.getText())
                + "\",\"tratamiento\":\"" + escapar(tratamiento.getText()) + "\"}";
        request("POST", "/api/clinica/consultas", cuerpo).thenAccept(r -> Platform.runLater(() -> {
            if (r.statusCode() == 201) {
                seleccionarMascota();
                mostrarInfo("Consulta guardada correctamente.");
            } else mostrarError("No se pudo guardar la consulta.");
        }));
    }

    private void enviarPrecuenta(ListView<String> detalles) {
        if (consultaSeleccionada == null) { mostrarError("Seleccione una consulta del historial."); return; }
        List<String> lineas = new ArrayList<>(detalles.getItems());
        if (lineas.isEmpty()) { mostrarError("Agregue al menos un producto o servicio."); return; }
        StringBuilder cuerpo = new StringBuilder("{\"consultaId\":")
                .append(consultaSeleccionada.path("id").asInt()).append(",\"detalles\":[");
        for (int i = 0; i < lineas.size(); i++) {
            String[] partes = lineas.get(i).split(" x ", 2);
            if (i > 0) cuerpo.append(',');
            cuerpo.append("{\"productoId\":").append(partes[0])
                    .append(",\"cantidad\":").append(partes[1]).append('}');
        }
        request("POST", "/api/ventas/precuentas", cuerpo.append("]}").toString())
                .thenAccept(r -> Platform.runLater(() -> {
                    if (r.statusCode() == 201) mostrarInfo("Precuenta enviada a caja.");
                    else mostrarError("No se pudo enviar la precuenta.");
                }));
    }

    private void verificarServidor() {
        request("GET", "/api/salud", null).thenAccept(r -> Platform.runLater(() ->
                estado.setText(r.statusCode() == 200 ? "Servidor: conectado" : "Servidor: error")))
                .exceptionally(e -> { Platform.runLater(() -> estado.setText("Servidor: sin conexión")); return null; });
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> request(
            String metodo, String ruta, String cuerpo) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SERVIDOR + ruta))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if ("POST".equals(metodo)) b.POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        else b.GET();
        return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private List<JsonNode> parseArray(String body) {
        try { List<JsonNode> lista = new ArrayList<>(); json.readTree(body).forEach(lista::add); return lista; }
        catch (Exception e) { return List.of(); }
    }
    private static String encode(String v) { return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8); }
    private static String escapar(String v) { return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
    private static String numero(TextField campo) { return campo.getText().isBlank() ? "null" : campo.getText().trim(); }
    private static HBox espacio() { HBox h = new HBox(); HBox.setHgrow(h, Priority.ALWAYS); return h; }
    private void mostrarError(String s) { mostrarMensaje(Alert.AlertType.ERROR, s); }
    private void mostrarInfo(String s) { mostrarMensaje(Alert.AlertType.INFORMATION, s); }
    private void mostrarMensaje(Alert.AlertType tipo, String mensaje) {
        Alert dialogo = new Alert(tipo, mensaje);
        dialogo.initOwner(ventanaPrincipal);
        dialogo.setTitle("Sistema Veterinaria · Consultorio");
        dialogo.setHeaderText(tipo == Alert.AlertType.ERROR ? "Revise la información" : "Operación completada");
        dialogo.getDialogPane().getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        dialogo.showAndWait();
    }
    public static void main(String[] args) { launch(args); }
}
