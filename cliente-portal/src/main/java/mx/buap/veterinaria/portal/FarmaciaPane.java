package mx.buap.veterinaria.portal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import static mx.buap.veterinaria.portal.Tablas.*;

/**
 * Farmacia e inventario avanzado: proveedores, compras con lote y caducidad, lotes (con ajuste por lote),
 * alertas de caducidad, sugerencia de reposición y márgenes por producto.
 */
final class FarmaciaPane {
    private final Api api;
    private final Stage owner;
    private final Label aviso = new Label();
    private final TableView<JsonNode> proveedores = new TableView<>(), compras = new TableView<>(), lotes = new TableView<>(),
            caducidades = new TableView<>(), reposicion = new TableView<>(), margenes = new TableView<>();
    private final List<JsonNode> catalogo = new ArrayList<>();
    private final CheckBox soloExistencia = new CheckBox("Solo con existencia");

    FarmaciaPane(Api api, Stage owner) { this.api = api; this.owner = owner; }

    Node crear() {
        col(proveedores, "Nombre", 200, n -> n.path("nombre").asText()); col(proveedores, "RFC", 110, n -> n.path("rfc").asText(""));
        col(proveedores, "Contacto", 140, n -> n.path("contacto").asText("")); col(proveedores, "Teléfono", 110, n -> n.path("telefono").asText(""));
        col(proveedores, "Correo", 170, n -> n.path("correo").asText("")); col(proveedores, "Activo", 60, n -> n.path("activo").asBoolean() ? "Sí" : "No");
        Button nuevoProv = new Button("Nuevo proveedor"); nuevoProv.getStyleClass().add("primary"); nuevoProv.setOnAction(e -> proveedor(null));
        Button editarProv = new Button("Editar"); editarProv.setOnAction(e -> { JsonNode p = proveedores.getSelectionModel().getSelectedItem(); if (p != null) proveedor(p); });
        Button activoProv = new Button("Activar / desactivar"); activoProv.setOnAction(e -> {
            JsonNode p = proveedores.getSelectionModel().getSelectedItem(); if (p == null) return;
            api.put("/api/farmacia/proveedores/" + p.path("id").asInt() + "/activo", api.objeto().put("activo", !p.path("activo").asBoolean()), r -> cargar(), this::error);
        });
        VBox tabProv = caja(proveedores, new HBox(8, nuevoProv, editarProv, activoProv));

        col(compras, "Fecha", 130, n -> fechaHora(n.path("fecha"))); col(compras, "Proveedor", 200, n -> n.path("proveedor").asText());
        col(compras, "Documento", 120, n -> n.path("documento").asText("")); col(compras, "Renglones", 80, n -> n.path("renglones").asText());
        col(compras, "Total", 110, n -> dinero(n.path("total"))); col(compras, "Registró", 140, n -> n.path("usuario").asText(""));
        Button nuevaCompra = new Button("Registrar compra / entrada con lote"); nuevaCompra.getStyleClass().add("primary"); nuevaCompra.setOnAction(e -> compra());
        VBox tabCompras = caja(compras, nuevaCompra);

        col(lotes, "Producto", 200, n -> n.path("producto").asText()); col(lotes, "Lote", 100, n -> n.path("numeroLote").asText());
        col(lotes, "Caducidad", 95, n -> n.path("caducidad").asText()); col(lotes, "Días", 60, n -> n.path("vencido").asBoolean() ? "VENCIDO" : n.path("diasRestantes").asText());
        col(lotes, "Existencia", 85, n -> num(n.path("cantidad"))); col(lotes, "Recibido", 80, n -> num(n.path("cantidadRecibida")));
        col(lotes, "Costo", 90, n -> dinero(n.path("costoUnitario"))); col(lotes, "Proveedor", 160, n -> n.path("proveedor").asText(""));
        soloExistencia.setSelected(true); soloExistencia.setOnAction(e -> cargarLotes());
        Button ajustar = new Button("Ajustar lote (merma / conteo)"); ajustar.setOnAction(e -> ajustarLote());
        VBox tabLotes = caja(lotes, new HBox(10, soloExistencia, ajustar));

        col(caducidades, "Producto", 220, n -> n.path("producto").asText()); col(caducidades, "Lote", 100, n -> n.path("numeroLote").asText());
        col(caducidades, "Caducidad", 100, n -> n.path("caducidad").asText());
        col(caducidades, "Estado", 120, n -> n.path("vencido").asBoolean() ? "VENCIDO" : "Vence en " + n.path("diasRestantes").asText() + " días");
        col(caducidades, "Existencia", 90, n -> num(n.path("cantidad"))); col(caducidades, "Proveedor", 160, n -> n.path("proveedor").asText(""));
        caducidades.setRowFactory(t -> new TableRow<>() { @Override protected void updateItem(JsonNode n, boolean v) { super.updateItem(n, v);
            setStyle(v || n == null ? "" : n.path("vencido").asBoolean() ? "-fx-background-color: #FBE3E1;" : n.path("diasRestantes").asLong() <= 15 ? "-fx-background-color: #FFF4D6;" : ""); } });
        VBox tabCad = caja(caducidades, new Label("Lotes con existencia que vencen en los próximos 60 días. Un lote vencido no se puede vender."));

        col(reposicion, "Producto", 220, n -> n.path("producto").asText()); col(reposicion, "Existencia", 85, n -> num(n.path("existencia")));
        col(reposicion, "Mínimo", 75, n -> num(n.path("minimo"))); col(reposicion, "Sugerido", 80, n -> num(n.path("sugerido")));
        col(reposicion, "Costo unit.", 90, n -> dinero(n.path("costoUnitario"))); col(reposicion, "Costo estimado", 110, n -> dinero(n.path("costoEstimado")));
        col(reposicion, "Último proveedor", 160, n -> n.path("ultimoProveedor").asText(""));
        VBox tabRepo = caja(reposicion, new Label("Sugerencia: llevar cada producto bajo mínimo al doble de su existencia mínima."));

        col(margenes, "Producto", 240, n -> n.path("producto").asText()); col(margenes, "Precio", 90, n -> dinero(n.path("precio")));
        col(margenes, "Último costo", 100, n -> dinero(n.path("costo"))); col(margenes, "Margen", 90, n -> dinero(n.path("margen")));
        col(margenes, "Margen %", 80, n -> n.path("margenPorcentaje").isNull() ? "—" : num(n.path("margenPorcentaje")) + " %");
        VBox tabMarg = caja(margenes, new Label("El costo es el de la última compra registrada."));

        TabPane tabs = new TabPane(new Tab("Proveedores", tabProv), new Tab("Compras", tabCompras), new Tab("Lotes", tabLotes),
                new Tab("Caducidades", tabCad), new Tab("Reposición", tabRepo), new Tab("Márgenes", tabMarg));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Button actualizar = new Button("Actualizar"); actualizar.setOnAction(e -> cargar());
        aviso.setWrapText(true);
        VBox raiz = new VBox(10, new HBox(10, titulo("FARMACIA E INVENTARIO AVANZADO"), actualizar), tabs, aviso);
        VBox.setVgrow(tabs, Priority.ALWAYS); raiz.getStyleClass().add("card");
        return raiz;
    }

    void cargar() {
        api.get("/api/farmacia/proveedores", l -> llenar(proveedores, l), this::error);
        api.get("/api/farmacia/compras", l -> llenar(compras, l), this::error);
        api.get("/api/farmacia/caducidades?dias=60", l -> llenar(caducidades, l), this::error);
        api.get("/api/farmacia/reposicion", l -> llenar(reposicion, l), this::error);
        api.get("/api/farmacia/margenes", l -> llenar(margenes, l), this::error);
        api.get("/api/productos/catalogo", l -> { catalogo.clear(); l.forEach(p -> { if ("PRODUCTO".equals(p.path("tipo").asText()) && p.path("activo").asBoolean(true)) catalogo.add(p); }); }, this::error);
        cargarLotes();
    }
    private void cargarLotes() { api.get("/api/farmacia/lotes?conExistencia=" + soloExistencia.isSelected(), l -> llenar(lotes, l), this::error); }

    private void proveedor(JsonNode p) {
        TextField nombre = new TextField(p == null ? "" : p.path("nombre").asText()), rfc = new TextField(p == null ? "" : p.path("rfc").asText("")),
                contacto = new TextField(p == null ? "" : p.path("contacto").asText("")), telefono = new TextField(p == null ? "" : p.path("telefono").asText("")),
                correo = new TextField(p == null ? "" : p.path("correo").asText("")), direccion = new TextField(p == null ? "" : p.path("direccion").asText("")),
                notas = new TextField(p == null ? "" : p.path("notas").asText(""));
        Label error = rojo(); Button guardar = new Button("Guardar"); guardar.getStyleClass().add("primary");
        Dialog<Void> d = dialogo(owner, p == null ? "Nuevo proveedor" : "Editar proveedor", new VBox(10,
                formulario(new String[]{"Nombre", "RFC", "Contacto", "Teléfono", "Correo", "Dirección", "Notas"}, nombre, rfc, contacto, telefono, correo, direccion, notas), error, guardar));
        guardar.setOnAction(e -> {
            ObjectNode c = api.objeto().put("nombre", nombre.getText().trim()).put("rfc", rfc.getText().trim().toUpperCase()).put("contacto", contacto.getText().trim())
                    .put("telefono", telefono.getText().trim()).put("correo", correo.getText().trim()).put("direccion", direccion.getText().trim()).put("notas", notas.getText().trim());
            if (c.path("correo").asText().isEmpty()) c.putNull("correo");
            guardar.setDisable(true);
            var ok = (java.util.function.Consumer<JsonNode>) r -> { d.close(); cargar(); info("Proveedor guardado."); };
            if (p == null) api.post("/api/farmacia/proveedores", c, ok, m -> { guardar.setDisable(false); error.setText(m); });
            else api.put("/api/farmacia/proveedores/" + p.path("id").asInt(), c, ok, m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.showAndWait();
    }

    private void compra() {
        ComboBox<JsonNode> proveedor = new ComboBox<>();
        proveedores.getItems().stream().filter(p -> p.path("activo").asBoolean()).forEach(proveedor.getItems()::add);
        proveedor.setCellFactory(v -> celda("nombre")); proveedor.setButtonCell(celda("nombre"));
        TextField documento = new TextField(); documento.setPromptText("Factura o remisión");
        GridPane renglones = new GridPane(); renglones.setHgap(6); renglones.setVgap(6);
        String[] cab = {"Producto", "Lote", "Caducidad", "Cantidad", "Costo unitario"};
        for (int i = 0; i < cab.length; i++) renglones.add(new Label(cab[i]), i, 0);
        record Fila(ComboBox<JsonNode> producto, TextField lote, DatePicker caducidad, TextField cantidad, TextField costo) {}
        List<Fila> filas = new ArrayList<>();
        Runnable agregar = () -> {
            ComboBox<JsonNode> prod = new ComboBox<>(FXCollections.observableArrayList(catalogo)); prod.setCellFactory(v -> celdaProducto()); prod.setButtonCell(celdaProducto()); prod.setPrefWidth(230);
            Fila f = new Fila(prod, new TextField(), new DatePicker(), new TextField(), new TextField());
            f.lote().setPrefColumnCount(8); f.cantidad().setPrefColumnCount(5); f.costo().setPrefColumnCount(6); f.caducidad().setPrefWidth(130);
            prod.valueProperty().addListener((o, a, b) -> { boolean cad = b != null && b.path("manejaCaducidad").asBoolean(); f.lote().setDisable(!cad); f.caducidad().setDisable(!cad);
                if (b != null && !b.path("costoUltimo").isNull() && f.costo().getText().isBlank()) f.costo().setText(b.path("costoUltimo").asText()); });
            int r = filas.size() + 1;
            renglones.addRow(r, prod, f.lote(), f.caducidad(), f.cantidad(), f.costo());
            filas.add(f);
        };
        agregar.run();
        Button otro = new Button("+ Agregar producto"); otro.setOnAction(e -> agregar.run());
        Label error = rojo(); Button guardar = new Button("Registrar compra"); guardar.getStyleClass().add("primary");
        Dialog<Void> d = dialogo(owner, "Compra / entrada de mercancía", new VBox(10,
                formulario(new String[]{"Proveedor", "Documento"}, proveedor, documento), new ScrollPane(renglones), otro,
                new Label("Los productos con caducidad requieren lote y fecha de caducidad futura."), error, guardar));
        d.getDialogPane().setPrefWidth(860);
        guardar.setOnAction(e -> {
            if (proveedor.getValue() == null) { error.setText("Seleccione el proveedor."); return; }
            ArrayNode productos = api.json.createArrayNode();
            try {
                for (Fila f : filas) {
                    if (f.producto().getValue() == null) continue;
                    ObjectNode r = productos.addObject().put("productoId", f.producto().getValue().path("id").asInt())
                            .put("cantidad", new BigDecimal(f.cantidad().getText().trim())).put("costoUnitario", new BigDecimal(f.costo().getText().trim().isEmpty() ? "0" : f.costo().getText().trim()));
                    if (!f.lote().isDisabled()) { r.put("numeroLote", f.lote().getText().trim()); if (f.caducidad().getValue() != null) r.put("caducidad", f.caducidad().getValue().toString()); }
                }
            } catch (NumberFormatException ex) { error.setText("Revise cantidades y costos."); return; }
            if (productos.isEmpty()) { error.setText("Agregue al menos un producto."); return; }
            ObjectNode c = api.objeto().put("proveedorId", proveedor.getValue().path("id").asInt()).put("documento", documento.getText().trim()); c.set("productos", productos);
            guardar.setDisable(true);
            api.post("/api/farmacia/compras", c, r -> { d.close(); cargar(); info("Compra registrada por " + dinero(r.path("total")) + "."); }, m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.showAndWait();
    }

    private void ajustarLote() {
        JsonNode l = lotes.getSelectionModel().getSelectedItem();
        if (l == null) { error("Seleccione un lote."); return; }
        TextField cantidad = new TextField(); cantidad.setPromptText("Negativo para merma, positivo para sobrante");
        TextField motivo = new TextField();
        Label error = rojo(); Button guardar = new Button("Aplicar ajuste"); guardar.getStyleClass().add("primary");
        Dialog<Void> d = dialogo(owner, "Ajuste del lote " + l.path("numeroLote").asText() + " · " + l.path("producto").asText(),
                new VBox(10, new Label("Existencia actual del lote: " + num(l.path("cantidad"))), formulario(new String[]{"Cantidad (+/-)", "Motivo"}, cantidad, motivo), error, guardar));
        guardar.setOnAction(e -> {
            BigDecimal c; try { c = new BigDecimal(cantidad.getText().trim()); } catch (NumberFormatException ex) { error.setText("Cantidad inválida."); return; }
            guardar.setDisable(true);
            api.put("/api/farmacia/lotes/" + l.path("id").asInt() + "/ajuste", api.objeto().put("cantidad", c).put("motivo", motivo.getText().trim()),
                    r -> { d.close(); cargar(); info("Ajuste aplicado."); }, m -> { guardar.setDisable(false); error.setText(m); });
        });
        d.showAndWait();
    }

    private static VBox caja(TableView<JsonNode> t, Node pie) {
        VBox v = new VBox(8, t, pie); v.setPadding(new Insets(10)); VBox.setVgrow(t, Priority.ALWAYS);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); return v;
    }
    private static ListCell<JsonNode> celda(String campo) {
        return new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e); setText(e || n == null ? null : n.path(campo).asText()); } };
    }
    private static ListCell<JsonNode> celdaProducto() {
        return new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e);
            setText(e || n == null ? null : n.path("nombre").asText() + (n.path("manejaCaducidad").asBoolean() ? " · con caducidad" : "")); } };
    }
    private void info(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #21865B;"); }
    private void error(String m) { aviso.setText(m); aviso.setStyle("-fx-text-fill: #B3261E;"); }
}
