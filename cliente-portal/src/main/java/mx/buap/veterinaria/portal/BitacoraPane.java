package mx.buap.veterinaria.portal;

import java.time.LocalDate;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import static mx.buap.veterinaria.portal.Tablas.*;

/** Consulta de la bitácora de auditoría (accesos, autorizaciones y cambios), solo lectura. */
final class BitacoraPane {
    private final Api api;
    private final DatePicker desde = new DatePicker(LocalDate.now().minusDays(7)), hasta = new DatePicker(LocalDate.now());
    private final TextField tabla = new TextField(), accion = new TextField();
    private final TableView<JsonNode> registros = new TableView<>();
    private final Label aviso = new Label();

    BitacoraPane(Api api) { this.api = api; }

    Node crear() {
        tabla.setPromptText("Tabla (ej. acceso, venta, cita)"); accion.setPromptText("Acción (ej. LOGIN_FALLIDO)");
        Button buscar = new Button("Buscar"); buscar.getStyleClass().add("primary"); buscar.setOnAction(e -> cargar());
        HBox filtros = new HBox(8, new Label("Del"), desde, new Label("al"), hasta, tabla, accion, buscar); filtros.setAlignment(Pos.CENTER_LEFT);
        col(registros, "Fecha", 140, n -> fechaHora(n.path("fecha"))); col(registros, "Usuario", 110, n -> n.path("usuario").isNull() ? "#" + n.path("usuarioId").asText("—") : n.path("usuario").asText());
        col(registros, "Acción", 140, n -> n.path("accion").asText()); col(registros, "Tabla", 110, n -> n.path("tabla").asText());
        col(registros, "Registro", 70, n -> n.path("registroId").asText()); col(registros, "Autorizó", 70, n -> n.path("autorizoId").asText(""));
        col(registros, "Equipo (IP)", 110, n -> n.path("ip").asText("")); col(registros, "Detalle", 360, n -> n.path("detalle").asText(""));
        registros.setPlaceholder(new Label("Sin registros en el rango."));
        aviso.setWrapText(true);
        VBox raiz = new VBox(10, titulo("BITÁCORA DE AUDITORÍA"), filtros, registros, aviso); VBox.setVgrow(registros, Priority.ALWAYS);
        raiz.getStyleClass().add("card");
        return raiz;
    }

    void cargar() {
        String q = "/api/auditoria?desde=" + desde.getValue() + "&hasta=" + hasta.getValue()
                + (tabla.getText().isBlank() ? "" : "&tabla=" + java.net.URLEncoder.encode(tabla.getText().trim(), java.nio.charset.StandardCharsets.UTF_8))
                + (accion.getText().isBlank() ? "" : "&accion=" + java.net.URLEncoder.encode(accion.getText().trim().toUpperCase(), java.nio.charset.StandardCharsets.UTF_8));
        api.get(q, l -> { llenar(registros, l); aviso.setText(l.size() + " registros (máximo 500, del más reciente al más antiguo)."); aviso.setStyle("-fx-text-fill: #425466;"); },
                m -> { aviso.setText(m); aviso.setStyle("-fx-text-fill: #B3261E;"); });
    }
}
