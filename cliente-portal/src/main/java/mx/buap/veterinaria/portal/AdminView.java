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

        addEmployee.getStyleClass().add("primary");
        addProduct.getStyleClass().add("primary");
        deactivateEmployee.getStyleClass().add("danger");
        deactivateProduct.getStyleClass().add("danger");
        FlowPane employeeActions = new FlowPane(8, 8, addEmployee, deactivateEmployee);
        FlowPane productActions = new FlowPane(8, 8, addProduct, editProduct, inventory, deactivateProduct);
        Label staffTitle = new Label("PERSONAL"); staffTitle.getStyleClass().add("section-title");
        Label productTitle = new Label("INVENTARIO / PRODUCTOS"); productTitle.getStyleClass().add("section-title");
        VBox left = new VBox(12, staffTitle, employeeActions, employees);
        VBox right = new VBox(12, productTitle, productActions, products);
        left.getStyleClass().add("card"); right.getStyleClass().add("card");
        VBox.setVgrow(employees, Priority.ALWAYS); VBox.setVgrow(products, Priority.ALWAYS);
        employees.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        products.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tab personal = new Tab("Personal", left); personal.setClosable(false);
        Tab inventario = new Tab("Productos e inventario", right); inventario.setClosable(false);
        TabPane sections = new TabPane(personal, inventario);
        BorderPane root = new BorderPane(sections);
        root.setTop(PortalApp.header("Administración  /  " + session.path("nombre").asText(), refresh));
        BorderPane.setMargin(sections, new Insets(18));
        status.getStyleClass().add("muted"); status.setWrapText(true);
        root.setBottom(status); BorderPane.setMargin(status, new Insets(0, 20, 16, 20));
        stage.setTitle("Sistema Veterinaria · Administración");
        stage.setMinWidth(820); stage.setMinHeight(600);
        stage.setScene(PortalApp.scene(root, 1100, 700));
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
        TextField name = new TextField();
        TextField user = new TextField();
        PasswordField password = new PasswordField(); password.setPromptText("Mínimo 8 caracteres");
        ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList("CAJERO", "VETERINARIO", "ADMINISTRADOR"));
        role.setValue("CAJERO");
        Dialog<String[]> dialog = form("Nuevo personal", "Datos del personal",
                new String[]{"Nombre", "Usuario", "Contraseña", "Rol"}, name, user, password, role);
        dialog.showAndWait().ifPresent(fields -> {
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
        TextField name = new TextField(selected == null ? "" : selected.path("nombre").asText());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("PRODUCTO", "SERVICIO"));
        type.setValue(selected == null ? "PRODUCTO" : selected.path("tipo").asText());
        TextField price = new TextField(selected == null ? "" : selected.path("precio").asText());
        TextField stock = new TextField(selected == null ? "0" : selected.path("stockActual").asText());
        TextField minimum = new TextField(selected == null ? "0" : selected.path("stockMinimo").asText());
        price.setPromptText("0.00");
        Dialog<String[]> dialog = form(selected == null ? "Nuevo producto" : "Editar producto", "Datos del producto o servicio",
                new String[]{"Nombre", "Tipo", "Precio ($)", "Existencias", "Stock mínimo"}, name, type, price, stock, minimum);
        dialog.showAndWait().ifPresent(fields -> {
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
        TextField quantity = new TextField(); quantity.setPromptText("Cantidad positiva");
        TextField reason = new TextField(); reason.setPromptText("Motivo de la entrada");
        Dialog<String[]> dialog = form("Entrada de inventario", selected.path("nombre").asText(),
                new String[]{"Cantidad", "Motivo"}, quantity, reason);
        dialog.showAndWait().ifPresent(fields -> {
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

    private Dialog<String[]> form(String title, String heading, String[] labels, Control... fields) {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.initOwner(stage); dialog.setTitle(title); dialog.setHeaderText(heading);
        PortalApp.styleDialog(dialog);
        GridPane grid = new GridPane(); grid.setHgap(16); grid.setVgap(12);
        grid.setPadding(new Insets(20));
        ColumnConstraints label = new ColumnConstraints(120);
        ColumnConstraints input = new ColumnConstraints(); input.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(label, input);
        for (int i = 0; i < fields.length; i++) {
            Label caption = new Label(labels[i]); caption.setLabelFor(fields[i]);
            fields[i].setMaxWidth(Double.MAX_VALUE);
            grid.addRow(i, caption, fields[i]);
        }
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(560);
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            String[] values = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = fields[i] instanceof TextInputControl text ? text.getText()
                        : String.valueOf(((ComboBox<?>) fields[i]).getValue());
            }
            return values;
        });
        return dialog;
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

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(stage);
        PortalApp.styleDialog(alert);
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
