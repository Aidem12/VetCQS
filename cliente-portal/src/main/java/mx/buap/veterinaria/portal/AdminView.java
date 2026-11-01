package mx.buap.veterinaria.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Administración protegida: todas las operaciones reutilizan el HttpClient de la sesión. */
final class AdminView {
    private final HttpClient http;
    private final String server;
    private final Stage stage;
    private final JsonNode session;
    private final ObjectMapper json = new ObjectMapper();
    private final TableView<JsonNode> employees = new TableView<>();
    private final TableView<JsonNode> products = new TableView<>();
    private final Label status = new Label();

    AdminView(HttpClient http, String server, Stage stage, JsonNode session) {
        this.http = http;
        this.server = server;
        this.stage = stage;
        this.session = session;
    }

    void show() {
        configureEmployees();
        configureProducts();
        Button refresh = new Button("Actualizar");
        refresh.setOnAction(e -> load());
        Button addEmployee = new Button("Nuevo personal");
        addEmployee.setOnAction(e -> employeeForm());
        Button deactivateEmployee = new Button("Desactivar seleccionado");
        deactivateEmployee.setOnAction(e -> deactivateEmployee());
        Button addProduct = new Button("Nuevo producto");
        addProduct.setOnAction(e -> productForm(null));
        Button editProduct = new Button("Editar seleccionado");
        editProduct.setOnAction(e -> productForm(products.getSelectionModel().getSelectedItem()));
        Button deactivateProduct = new Button("Desactivar producto");
        deactivateProduct.setOnAction(e -> deactivateProduct());
        Button inventory = new Button("Registrar entrada");
        inventory.setOnAction(e -> inventoryForm());

        HBox employeeActions = new HBox(8, addEmployee, deactivateEmployee);
        HBox productActions = new HBox(8, addProduct, editProduct, deactivateProduct, inventory);
        VBox left = new VBox(8, new Label("PERSONAL"), employees, employeeActions);
        VBox right = new VBox(8, new Label("INVENTARIO / PRODUCTOS"), products, productActions);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(employees, Priority.ALWAYS);
        VBox.setVgrow(products, Priority.ALWAYS);
        Label heading = new Label("Administración · " + session.path("nombre").asText());
        heading.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");
        BorderPane root = new BorderPane(new HBox(12, left, right));
        root.setTop(heading);
        root.setBottom(status);
        root.setPadding(new Insets(18));
        stage.setTitle("Sistema Veterinaria · Administración");
        stage.setScene(new Scene(root, 1100, 650));
        stage.show();
        load();
    }

    private void configureEmployees() {
        employees.setPlaceholder(new Label("Sin personal"));
        addColumn(employees, "Id", "id", 55);
        addColumn(employees, "Nombre", "nombre", 150);
        addColumn(employees, "Usuario", "usuario", 120);
        addColumn(employees, "Rol", "rol", 120);
        addColumn(employees, "Activo", "activo", 70);
    }

    private void configureProducts() {
        products.setPlaceholder(new Label("Sin productos"));
        addColumn(products, "Id", "id", 55);
        addColumn(products, "Nombre", "nombre", 150);
        addColumn(products, "Tipo", "tipo", 100);
        addColumn(products, "Precio", "precio", 90);
        addColumn(products, "Stock", "stockActual", 90);
        addColumn(products, "Activo", "activo", 70);
    }

    private static void addColumn(TableView<JsonNode> table, String title, String key, int width) {
        TableColumn<JsonNode, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().path(key).isMissingNode() ? "" : data.getValue().path(key).asText()));
        table.getColumns().add(column);
    }

    private void load() {
        request("GET", "/api/empleados", "").thenAccept(r -> Platform.runLater(() -> fill(employees, r)));
        request("GET", "/api/productos", "").thenAccept(r -> Platform.runLater(() -> fill(products, r)));
    }

    private void fill(TableView<JsonNode> table, HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            showError(response);
            return;
        }
        try {
            table.setItems(FXCollections.observableArrayList(json.readTree(response.body())));
            status.setText("Datos actualizados.");
        } catch (Exception e) {
            status.setText("Respuesta inválida del servidor.");
        }
    }

    private void employeeForm() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuevo personal");
        dialog.setHeaderText("nombre,usuario,contraseña (8+),rol");
        dialog.showAndWait().ifPresent(value -> {
            String[] fields = value.split(",", -1);
            if (fields.length != 4 || fields[0].isBlank() || fields[1].isBlank() || fields[2].length() < 8
                    || !isRole(fields[3])) {
                status.setText("Datos inválidos. Rol: ADMINISTRADOR, CAJERO o VETERINARIO.");
                return;
            }
            ObjectNode body = json.createObjectNode();
            body.put("nombre", fields[0].trim());
            body.put("usuario", fields[1].trim());
            body.put("contrasena", fields[2]);
            body.put("rol", fields[3].trim().toUpperCase());
            submit("POST", "/api/empleados", body, "Personal creado.");
        });
    }

    private void deactivateEmployee() {
        JsonNode selected = employees.getSelectionModel().getSelectedItem();
        if (selected == null || !confirm("¿Desactivar el personal seleccionado?")) return;
        submit("PUT", "/api/empleados/" + selected.path("id").asInt() + "/desactivar",
                json.createObjectNode(), "Personal desactivado.");
    }

    private void productForm(JsonNode selected) {
        String initial = selected == null ? "" : String.join(",",
                selected.path("nombre").asText(), selected.path("tipo").asText(),
                selected.path("precio").asText(), selected.path("stockActual").asText(),
                selected.path("stockMinimo").asText());
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(selected == null ? "Nuevo producto" : "Editar producto");
        dialog.setHeaderText("nombre,tipo(PRODUCTO|SERVICIO),precio,stock,stockMinimo");
        dialog.showAndWait().ifPresent(value -> {
            String[] fields = value.split(",", -1);
            if (fields.length != 5 || fields[0].isBlank() || !fields[1].equalsIgnoreCase("PRODUCTO")
                    && !fields[1].equalsIgnoreCase("SERVICIO")) {
                status.setText("Datos de producto inválidos.");
                return;
            }
            try {
                ObjectNode body = json.createObjectNode();
                body.put("nombre", fields[0].trim());
                body.put("tipo", fields[1].trim().toUpperCase());
                body.put("precio", new BigDecimal(fields[2].trim()));
                body.put("stockActual", new BigDecimal(fields[3].trim()));
                body.put("stockMinimo", new BigDecimal(fields[4].trim()));
                body.put("manejaCaducidad", false);
                String path = selected == null ? "/api/productos" : "/api/productos/" + selected.path("id").asInt();
                submit(selected == null ? "POST" : "PUT", path, body, "Producto guardado.");
            } catch (RuntimeException e) {
                status.setText("Precio y existencias deben ser numéricos.");
            }
        });
    }

    private void deactivateProduct() {
        JsonNode selected = products.getSelectionModel().getSelectedItem();
        if (selected == null || !confirm("¿Desactivar el producto seleccionado?")) return;
        submit("DELETE", "/api/productos/" + selected.path("id").asInt(),
                json.createObjectNode(), "Producto desactivado.");
    }

    private void inventoryForm() {
        JsonNode selected = products.getSelectionModel().getSelectedItem();
        if (selected == null) {
            status.setText("Seleccione un producto.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Entrada de inventario");
        dialog.setHeaderText("cantidad positiva,motivo");
        dialog.showAndWait().ifPresent(value -> {
            String[] fields = value.split(",", 2);
            if (fields.length != 2 || fields[1].isBlank()) {
                status.setText("Capture cantidad y motivo.");
                return;
            }
            try {
                ObjectNode body = json.createObjectNode();
                body.put("cantidad", new BigDecimal(fields[0].trim()));
                body.put("usuarioId", session.path("id").asInt());
                body.put("motivo", fields[1].trim());
                submit("POST", "/api/inventario/" + selected.path("id").asInt() + "/entradas",
                        body, "Entrada registrada.");
            } catch (RuntimeException e) {
                status.setText("La cantidad debe ser numérica.");
            }
        });
    }

    private void submit(String method, String path, ObjectNode body, String success) {
        request(method, path, body.toString()).thenAccept(response -> Platform.runLater(() -> {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                status.setText(success);
                load();
            } else showError(response);
        }));
    }

    private void showError(HttpResponse<String> response) {
        status.setText("Servidor rechazó la operación (" + response.statusCode() + ").");
    }

    private static boolean isRole(String role) {
        return role.equalsIgnoreCase("ADMINISTRADOR") || role.equalsIgnoreCase("CAJERO")
                || role.equalsIgnoreCase("VETERINARIO");
    }

    private static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private CompletableFuture<HttpResponse<String>> request(String method, String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server + path))
                    .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json");
            HttpRequest request = method.equals("GET") ? builder.GET().build()
                    : method.equals("DELETE") ? builder.DELETE().build()
                    : builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .exceptionally(error -> new FailedResponse(503));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new FailedResponse(503));
        }
    }

    private static final class FailedResponse implements HttpResponse<String> {
        private final int status;
        FailedResponse(int status) { this.status = status; }
        public int statusCode() { return status; }
        public HttpRequest request() { return null; }
        public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
        public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        public String body() { return ""; }
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
        public URI uri() { return URI.create("http://localhost"); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
