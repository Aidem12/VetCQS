package mx.buap.veterinaria.portal;

import java.io.File;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/** Utilidades de interfaz compartidas por las secciones nuevas de Administración. */
final class Tablas {
    private Tablas() {}
    static final NumberFormat PESOS = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"));

    static void col(TableView<JsonNode> t, String titulo, int ancho, Function<JsonNode, String> v) {
        TableColumn<JsonNode, String> c = new TableColumn<>(titulo); c.setPrefWidth(ancho);
        c.setCellValueFactory(x -> new SimpleStringProperty(v.apply(x.getValue()))); t.getColumns().add(c);
        if (t.getPlaceholder() == null) t.setPlaceholder(new Label("Sin registros."));
    }
    static void llenar(TableView<JsonNode> t, JsonNode lista) { List<JsonNode> l = new ArrayList<>(); lista.forEach(l::add); t.getItems().setAll(l); }
    static String dinero(JsonNode n) { return n == null || n.isNull() || n.isMissingNode() ? "—" : PESOS.format(n.decimalValue()); }
    static String dinero(BigDecimal n) { return n == null ? "—" : PESOS.format(n); }
    static String num(JsonNode n) { return n == null || n.isNull() || n.isMissingNode() ? "—" : n.decimalValue().stripTrailingZeros().toPlainString(); }
    static String fechaHora(JsonNode n) { return n.asText("").replace('T', ' ').replaceFirst(":\\d\\d(\\..*)?$", ""); }

    static GridPane formulario(String[] etiquetas, Control... campos) {
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8);
        ColumnConstraints a = new ColumnConstraints(130), b = new ColumnConstraints(); b.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(a, b);
        for (int i = 0; i < campos.length; i++) { g.add(new Label(etiquetas[i]), 0, i); campos[i].setMaxWidth(Double.MAX_VALUE); g.add(campos[i], 1, i); }
        return g;
    }

    static Dialog<Void> dialogo(Window owner, String titulo, Node contenido) {
        Dialog<Void> d = new Dialog<>(); d.initOwner(owner); d.setTitle(titulo); d.setHeaderText(titulo); d.setResizable(true);
        if (owner != null && owner.getScene() != null) d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setContent(contenido); d.getDialogPane().setPrefWidth(620);
        return d;
    }

    static Label titulo(String t) { Label l = new Label(t); l.getStyleClass().add("section-title"); return l; }
    static Label rojo() { Label l = new Label(); l.setWrapText(true); l.setStyle("-fx-text-fill: #B3261E;"); return l; }
    static void abrir(File f) { try { if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(f); } catch (Throwable ignorada) { } }
}
