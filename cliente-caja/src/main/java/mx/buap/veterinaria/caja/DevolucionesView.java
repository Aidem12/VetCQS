package mx.buap.veterinaria.caja;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Devolución o reembolso de una venta pagada (F6). Requiere turno abierto, motivo y autorización
 * de un administrador; el servidor valida cantidades, efectivo disponible e inventario.
 */
final class DevolucionesView {
    private final HttpClient http;
    private final Stage owner;
    private final ObjectMapper json = new ObjectMapper();
    private final String encabezadoTicket;

    DevolucionesView(HttpClient http, Stage owner, String encabezadoTicket) {
        this.http = http; this.owner = owner; this.encabezadoTicket = encabezadoTicket;
    }

    void mostrar() {
        TextInputDialog pedir = new TextInputDialog();
        pedir.initOwner(owner); estilo(pedir.getDialogPane());
        pedir.setTitle("Devolución"); pedir.setHeaderText("Folio o número de la venta pagada");
        pedir.setContentText("Folio (ej. V-25 o 25):");
        pedir.showAndWait().map(t -> t.replaceAll("\\D", "")).filter(t -> !t.isBlank()).ifPresent(id ->
            enviar("GET", "/api/ventas/" + id + "/devoluciones/disponibles", null, lista -> formulario(Integer.parseInt(id), lista),
                   mensaje -> alerta(Alert.AlertType.WARNING, mensaje)));
    }

    private void formulario(int ventaId, JsonNode disponibles) {
        Dialog<Void> d = new Dialog<>(); d.initOwner(owner); estilo(d.getDialogPane());
        d.setTitle("Devolución de la venta V-" + ventaId); d.setHeaderText("Indique cantidades a devolver (0 = no se devuelve)");
        GridPane tabla = new GridPane(); tabla.setHgap(10); tabla.setVgap(6);
        tabla.addRow(0, negrita("Producto"), negrita("Disponible"), negrita("Precio neto"), negrita("Devolver"), negrita("Reingresa"));
        List<TextField> cantidades = new ArrayList<>(); List<CheckBox> reingresos = new ArrayList<>(); List<JsonNode> filas = new ArrayList<>();
        int fila = 1;
        for (JsonNode p : disponibles) {
            TextField cant = new TextField("0"); cant.setPrefColumnCount(5);
            CheckBox reingresa = new CheckBox(); reingresa.setSelected("PRODUCTO".equals(p.path("tipo").asText()));
            reingresa.setDisable(!"PRODUCTO".equals(p.path("tipo").asText()));
            reingresa.setTooltip(new Tooltip("Desmarque si el producto regresó dañado (se registra como merma, no vuelve al inventario)."));
            if (p.path("disponible").decimalValue().signum() <= 0) cant.setDisable(true);
            tabla.addRow(fila++, new Label(p.path("producto").asText()), new Label(p.path("disponible").decimalValue().stripTrailingZeros().toPlainString()),
                    new Label("$" + p.path("precioNeto").asText()), cant, reingresa);
            cantidades.add(cant); reingresos.add(reingresa); filas.add(p);
        }
        ComboBox<String> forma = new ComboBox<>(FXCollections.observableArrayList("EFECTIVO", "TARJETA", "TRANSFERENCIA")); forma.setValue("EFECTIVO");
        TextField motivo = new TextField(); motivo.setPromptText("Ej. producto en mal estado");
        TextField usuario = new TextField(); PasswordField clave = new PasswordField();
        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("error-text");
        Button confirmar = new Button("Registrar devolución"); confirmar.getStyleClass().add("primary");
        VBox contenido = new VBox(10, tabla, new Label("Forma de reembolso"), forma, new Label("Motivo"), motivo,
                new Label("Usuario administrador"), usuario, new Label("Contraseña"), clave, error, confirmar);
        contenido.setPadding(new Insets(6));
        d.getDialogPane().setContent(new ScrollPane(contenido)); d.getDialogPane().setPrefSize(640, 560);
        d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        d.getDialogPane().lookupButton(ButtonType.CANCEL).disableProperty().bind(confirmar.disabledProperty());
        d.setOnCloseRequest(e -> { if (confirmar.isDisabled()) e.consume(); });
        confirmar.setOnAction(e -> {
            error.setText("");
            ArrayNode renglones = json.createArrayNode();
            try {
                for (int i = 0; i < filas.size(); i++) {
                    BigDecimal c = new BigDecimal(cantidades.get(i).getText().trim().isEmpty() ? "0" : cantidades.get(i).getText().trim());
                    if (c.signum() < 0) throw new NumberFormatException();
                    if (c.signum() > 0) renglones.addObject().put("productoId", filas.get(i).path("productoId").asInt())
                            .put("cantidad", c).put("reingresa", reingresos.get(i).isSelected());
                }
            } catch (NumberFormatException ex) { error.setText("Revise las cantidades."); return; }
            if (renglones.isEmpty()) { error.setText("Indique al menos una cantidad a devolver."); return; }
            if (motivo.getText().isBlank() || usuario.getText().isBlank() || clave.getText().isEmpty()) {
                error.setText("Complete el motivo y la autorización del administrador."); return;
            }
            ObjectNode cuerpo = json.createObjectNode();
            cuerpo.set("renglones", renglones);
            cuerpo.put("motivo", motivo.getText().trim()).put("formaReembolso", forma.getValue())
                    .put("usuarioAdmin", usuario.getText().trim()).put("claveAdmin", clave.getText());
            confirmar.setDisable(true);
            enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", cuerpo, r -> {
                confirmar.setDisable(false); clave.clear(); d.close(); ticket(r);
            }, mensaje -> { confirmar.setDisable(false); clave.clear(); error.setText(mensaje); });
        });
        d.showAndWait();
    }

    private void ticket(JsonNode dev) {
        StringBuilder t = new StringBuilder(encabezadoTicket).append("\n--------------------------------\n")
                .append("COMPROBANTE DE DEVOLUCIÓN\n").append("Folio: ").append(dev.path("folio").asText())
                .append("\nVenta: V-").append(dev.path("ventaId").asInt())
                .append("\nFecha: ").append(dev.path("fecha").asText().replace('T', ' ').replaceFirst("\\..*", ""))
                .append("\nMotivo: ").append(dev.path("motivo").asText()).append("\n--------------------------------\n");
        dev.path("detalles").forEach(x -> t.append(x.path("cantidad").decimalValue().stripTrailingZeros().toPlainString()).append(" x ")
                .append(x.path("producto").asText()).append("\n   $").append(x.path("importe").asText())
                .append(x.path("reingresa").asBoolean() ? "" : "  (merma)").append("\n"));
        t.append("--------------------------------\nREEMBOLSO ").append(dev.path("formaReembolso").asText()).append(": $").append(dev.path("monto").asText());
        Label texto = new Label(t.toString()); texto.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
        Alert a = new Alert(Alert.AlertType.INFORMATION); a.initOwner(owner); estilo(a.getDialogPane());
        a.setTitle("Devolución registrada"); a.setHeaderText(null); a.setGraphic(null); a.getDialogPane().setContent(texto);
        a.showAndWait();
    }

    private void enviar(String metodo, String ruta, JsonNode cuerpo, Consumer<JsonNode> ok, Consumer<String> falla) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(CajaApp.SERVIDOR + ruta)).timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json");
        Csrf.aplicar(http, CajaApp.SERVIDOR, metodo, b);
        HttpRequest req = "GET".equals(metodo) ? b.GET().build() : b.POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())).build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((r, ex) -> Platform.runLater(() -> {
            if (ex != null) { falla.accept("No se pudo conectar al servidor."); return; }
            JsonNode nodo;
            try { nodo = r.body().isBlank() ? json.nullNode() : json.readTree(r.body()); } catch (Exception e) { nodo = json.nullNode(); }
            if (r.statusCode() >= 200 && r.statusCode() < 300) ok.accept(nodo);
            else if (r.statusCode() == 404) falla.accept("No existe una venta con ese folio.");
            else falla.accept(nodo.path("message").asText(nodo.path("error").asText("Operación rechazada (" + r.statusCode() + ").")));
        }));
    }

    private static Label negrita(String t) { Label l = new Label(t); l.setStyle("-fx-font-weight: bold;"); return l; }
    private void estilo(DialogPane p) { p.getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm()); }
    private void alerta(Alert.AlertType tipo, String m) { Alert a = new Alert(tipo, m); a.initOwner(owner); estilo(a.getDialogPane()); a.showAndWait(); }
}
