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
    private final Runnable logout;
    private final HttpClient http;
    private final String server;
    private final Stage stage;
    private final JsonNode session;
    private final ObjectMapper json = new ObjectMapper();
    private final TableView<JsonNode> employees = new TableView<>();
    private final TableView<JsonNode> products = new TableView<>();
    private final Label status = new Label();
    private final Label connection = new Label("Servidor: verificando...");
    /** Iteración 3: aviso de productos en o por debajo del stock mínimo (RF-14). */
    private final Label stockAlert = new Label();

    AdminView(HttpClient http, String server, Stage stage, JsonNode session, Runnable logout) {
        this.logout = logout;
        this.http = http;
        this.server = server;
        this.stage = stage;
        this.session = session;
    }

    void show() {
        Button cerrarSesion = new Button("Cerrar sesión");
        cerrarSesion.getStyleClass().add("logout");
        cerrarSesion.setOnAction(e -> logout.run());
        configureEmployees();
        configureProducts();
        Button refresh = new Button("Actualizar · F5");
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
        Button adjust = new Button("Ajustar existencia");
        adjust.setOnAction(e -> adjustmentForm());
        Button kardex = new Button("Ver movimientos");
        kardex.setOnAction(e -> showKardex());
        stockAlert.getStyleClass().add("stock-alert");
        stockAlert.setWrapText(true); stockAlert.setMaxWidth(Double.MAX_VALUE);
        stockAlert.setVisible(false); stockAlert.setManaged(false);

        addEmployee.getStyleClass().add("primary");
        addProduct.getStyleClass().add("primary");
        deactivateEmployee.getStyleClass().add("danger");
        deactivateProduct.getStyleClass().add("danger");
        FlowPane employeeActions = new FlowPane(8, 8, addEmployee, deactivateEmployee);
        FlowPane productActions = new FlowPane(8, 8, addProduct, editProduct, inventory, adjust, kardex, deactivateProduct);
        Label staffTitle = new Label("PERSONAL"); staffTitle.getStyleClass().add("section-title");
        Label productTitle = new Label("INVENTARIO / PRODUCTOS"); productTitle.getStyleClass().add("section-title");
        VBox left = new VBox(12, staffTitle, employeeActions, employees);
        VBox right = new VBox(12, productTitle, productActions, stockAlert, products);
        left.getStyleClass().add("card"); right.getStyleClass().add("card");
        VBox.setVgrow(employees, Priority.ALWAYS); VBox.setVgrow(products, Priority.ALWAYS);
        employees.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        products.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tab personal = new Tab("Personal", left); personal.setClosable(false);
        Tab inventario = new Tab("Productos e inventario", right); inventario.setClosable(false);
        Button cortes = new Button("Historial de cortes");
        cortes.setOnAction(e -> new CortesView(http, stage, server).historial());
        connection.getStyleClass().add("connection-status");
        TabPane sections = new TabPane(personal, inventario);
        BorderPane root = new BorderPane(sections);
        root.setTop(PortalApp.header("Administración  /  " + session.path("nombre").asText(), new HBox(8, connection, cortes, refresh, cerrarSesion)));
        BorderPane.setMargin(sections, new Insets(18));
        status.getStyleClass().add("muted"); status.setWrapText(true);
        root.setBottom(status); BorderPane.setMargin(status, new Insets(0, 20, 16, 20));
        stage.setTitle("Sistema Veterinaria · Administración");
        stage.setMinWidth(820); stage.setMinHeight(600);
        javafx.scene.Scene scene = PortalApp.scene(root, 1100, 700);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.F5) { load(); e.consume(); }
        });
        stage.setScene(scene);
        stage.show();
        load();
    }

    private void configureEmployees() {
        employees.setId("personal");
        employees.setPlaceholder(new Label("Sin personal"));
        addColumn(employees, "Id", "id", 55);
        addColumn(employees, "Nombre", "nombre", 150);
        addColumn(employees, "Usuario", "usuario", 120);
        addColumn(employees, "Rol", "rol", 120);
        addColumn(employees, "Activo", "activo", 70);
    }

    private void configureProducts() {
        products.setId("catalogo");
        products.setPlaceholder(new Label("Sin productos"));
        addColumn(products, "Id", "id", 55);
        addColumn(products, "Nombre", "nombre", 150);
        addColumn(products, "Tipo", "tipo", 100);
        addColumn(products, "Precio", "precio", 90);
        addColumn(products, "Stock", "stockActual", 80);
        addColumn(products, "Mínimo", "stockMinimo", 80);
        addColumn(products, "Activo", "activo", 70);
        // Iteración 3: los productos en o bajo su mínimo se ven en rojo.
        products.setRowFactory(table -> new TableRow<>() {
            @Override protected void updateItem(JsonNode item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("stock-low");
                if (!empty && item != null && isLowStock(item)) getStyleClass().add("stock-low");
            }
        });
    }

    static boolean isLowStock(JsonNode product) {
        return "PRODUCTO".equals(product.path("tipo").asText()) && product.path("activo").asBoolean(true)
                && product.path("stockActual").decimalValue().compareTo(product.path("stockMinimo").decimalValue()) <= 0;
    }

    private static void addColumn(TableView<JsonNode> table, String title, String key, int width) {
        TableColumn<JsonNode, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().path(key).isMissingNode() ? "" : data.getValue().path(key).asText()));
        table.getColumns().add(column);
    }

    private void load() {
        request("GET", "/api/salud", "").thenAccept(r -> Platform.runLater(() -> {
            boolean ok = r.statusCode() == 200; connection.setText(ok ? "Servidor: conectado" : "Servidor: sin conexión");
            connection.getStyleClass().removeAll("connected", "disconnected"); connection.getStyleClass().add(ok ? "connected" : "disconnected");
        }));
        request("GET", "/api/empleados", "").thenAccept(r -> Platform.runLater(() -> fill(employees, r)));
        request("GET", "/api/productos/catalogo", "").thenAccept(r -> Platform.runLater(() -> fill(products, r)));
        request("GET", "/api/inventario/alertas", "").thenAccept(r -> Platform.runLater(() -> showStockAlerts(r)));
    }

    private void showStockAlerts(HttpResponse<String> response) {
        int count = 0;
        StringBuilder names = new StringBuilder();
        try {
            if (response.statusCode() == 200) {
                for (JsonNode alert : json.readTree(response.body())) {
                    if (count < 4) names.append(count == 0 ? "" : ", ").append(alert.path("nombre").asText());
                    count++;
                }
            }
        } catch (Exception ignored) { count = 0; }
        boolean visible = count > 0;
        stockAlert.setVisible(visible); stockAlert.setManaged(visible);
        if (visible) {
            stockAlert.setText("⚠  " + count + (count == 1 ? " producto llegó" : " productos llegaron")
                    + " a su stock mínimo: " + names + (count > 4 ? " y " + (count - 4) + " más" : "")
                    + ". Registre una entrada para reabastecer.");
        }
    }

    private void fill(TableView<JsonNode> table, HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            showError(response);
            return;
        }
        try {
            JsonNode rows = json.readTree(response.body());
            if (!rows.isArray()) throw new IllegalArgumentException("Se esperaba una lista");
            table.getItems().clear(); rows.forEach(table.getItems()::add);
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
        TextField barcode = new TextField(selected == null ? "" : selected.path("codigoBarras").asText(""));
        TextField name = new TextField(selected == null ? "" : selected.path("nombre").asText());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("PRODUCTO", "SERVICIO"));
        type.setValue(selected == null ? "PRODUCTO" : selected.path("tipo").asText());
        TextField price = new TextField(selected == null ? "" : selected.path("precio").asText());
        TextField stock = new TextField(selected == null ? "0" : selected.path("stockActual").asText());
        if (selected != null) {
            // Iteración 3: la existencia de un producto ya creado solo cambia con entradas o ajustes (queda en el kardex).
            stock.setDisable(true);
            stock.setTooltip(new Tooltip("Use Registrar entrada o Ajustar existencia"));
        }
        TextField minimum = new TextField(selected == null ? "0" : selected.path("stockMinimo").asText());
        price.setPromptText("0.00");
        Dialog<String[]> dialog = form(selected == null ? "Nuevo producto" : "Editar producto", "Datos del producto o servicio",
                new String[]{"Nombre", "Tipo", "Precio ($)", "Existencias", "Stock mínimo", "Código de barras"}, name, type, price, stock, minimum, barcode);
        dialog.showAndWait().ifPresent(fields -> {
            if (fields.length != 6 || fields[0].isBlank() || !fields[1].equalsIgnoreCase("PRODUCTO")
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
                body.put("codigoBarras", fields[5].trim());
                body.put("manejaCaducidad", selected != null && selected.path("manejaCaducidad").asBoolean());
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

    private void adjustmentForm() {
        JsonNode selected = products.getSelectionModel().getSelectedItem();
        if (selected == null || !"PRODUCTO".equals(selected.path("tipo").asText())) {
            status.setText("Seleccione un producto (los servicios no manejan existencias).");
            return;
        }
        ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList(
                "Salida: merma o daño", "Salida: caducidad", "Corrección por conteo físico (+/-)"));
        kind.setValue("Salida: merma o daño");
        TextField quantity = new TextField(); quantity.setPromptText("Ej. 2  (en conteo físico use -2 para restar)");
        TextField reason = new TextField(); reason.setPromptText("Detalle: lote, quién lo detectó, etc.");
        Dialog<String[]> dialog = form("Ajuste de inventario", selected.path("nombre").asText()
                        + "  ·  existencia actual: " + selected.path("stockActual").asText(),
                new String[]{"Tipo de ajuste", "Cantidad", "Detalle"}, kind, quantity, reason);
        dialog.showAndWait().ifPresent(fields -> {
            if (fields.length != 3 || fields[2].isBlank()) {
                status.setText("Capture cantidad y detalle del ajuste.");
                return;
            }
            try {
                BigDecimal amount = new BigDecimal(fields[1].trim());
                // Merma y caducidad siempre restan; el conteo físico respeta el signo capturado.
                if (!fields[0].startsWith("Corrección")) amount = amount.abs().negate();
                if (amount.signum() == 0) {
                    status.setText("La cantidad del ajuste no puede ser cero.");
                    return;
                }
                ObjectNode body = json.createObjectNode();
                body.put("cantidad", amount);
                body.put("usuarioId", session.path("id").asInt());
                body.put("motivo", fields[0].replace(" (+/-)", "") + " · " + fields[2].trim());
                submit("POST", "/api/inventario/" + selected.path("id").asInt() + "/ajustes",
                        body, "Ajuste registrado.");
            } catch (RuntimeException e) {
                status.setText("La cantidad debe ser numérica.");
            }
        });
    }

    /** Kardex: cada movimiento del producto con la existencia que dejó. */
    private void showKardex() {
        JsonNode selected = products.getSelectionModel().getSelectedItem();
        if (selected == null) {
            status.setText("Seleccione un producto.");
            return;
        }
        request("GET", "/api/inventario/" + selected.path("id").asInt() + "/kardex", "")
                .thenAccept(r -> Platform.runLater(() -> {
                    if (r.statusCode() != 200) { showError(r); return; }
                    try { kardexDialog(selected, json.readTree(r.body())); }
                    catch (Exception e) { status.setText("Respuesta inválida del servidor."); }
                }));
    }

    private void kardexDialog(JsonNode product, JsonNode rows) {
        TableView<JsonNode> table = new TableView<>();
        table.setPlaceholder(new Label("Este producto todavía no tiene movimientos."));
        TableColumn<JsonNode, String> date = new TableColumn<>("Fecha");
        date.setPrefWidth(130);
        date.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().path("fecha").asText().replace('T', ' ').replaceAll("\\.\\d+$", "")
                        .replaceAll(":\\d\\d$", "")));
        table.getColumns().add(date);
        addColumn(table, "Tipo", "tipo", 95);
        addColumn(table, "Cantidad", "cantidad", 80);
        addColumn(table, "Existencia", "saldo", 85);
        addColumn(table, "Motivo", "motivo", 230);
        addColumn(table, "Referencia", "referencia", 110);
        addColumn(table, "Usuario", "usuario", 120);
        table.getColumns().get(2).getStyleClass().add("money-column");
        table.getColumns().get(3).getStyleClass().addAll("money-column", "amount-column");
        rows.forEach(table.getItems()::add);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(380);
        Label summary = new Label("Existencia actual: " + product.path("stockActual").asText()
                + "   ·   Mínimo: " + product.path("stockMinimo").asText()
                + "   ·   " + rows.size() + (rows.size() == 1 ? " movimiento" : " movimientos"));
        summary.getStyleClass().add("muted");
        VBox content = new VBox(10, summary, table);
        content.setPadding(new Insets(16));
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Movimientos de inventario");
        dialog.setHeaderText(product.path("nombre").asText());
        PortalApp.styleDialog(dialog);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(900);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
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
        try { JsonNode error = json.readTree(response.body()); status.setText(error.path("message").asText(error.path("error").asText("Operación rechazada (" + response.statusCode() + ")."))); }
        catch (Exception e) { status.setText("No se pudo completar la operación (" + response.statusCode() + ")."); }
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
