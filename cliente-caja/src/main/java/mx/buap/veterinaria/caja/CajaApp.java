package mx.buap.veterinaria.caja;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Esqueleto de la pantalla de caja (prototipo aprobado en la semana 3).
 * En la iteración 1 (semana 4) se conecta la tabla con /api/productos y se agrega el cobro.
 */
public class CajaApp extends Application {

    /** Dirección del servidor; se puede cambiar con -Dservidor=http://IP:8080 */
    static final String SERVIDOR = System.getProperty("servidor", "http://localhost:8080");

    private final Label estadoServidor = new Label("Servidor: verificando…");

    @Override
    public void start(Stage ventana) {
        Label encabezado = new Label("Caja · Turno sin abrir");
        HBox barraSuperior = new HBox(16, encabezado, espacio(), estadoServidor);
        barraSuperior.setPadding(new Insets(8, 12, 8, 12));
        barraSuperior.setStyle("-fx-background-color: #C9CFD7;");

        TextField captura = new TextField();
        captura.setPromptText("Escanee o escriba código o nombre (F1)");

        TableView<Object> renglones = new TableView<>();
        for (String titulo : new String[] {"Cant.", "Producto", "Precio", "Importe"}) {
            renglones.getColumns().add(new TableColumn<>(titulo));
        }
        renglones.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Label total = new Label("$0.00");
        total.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        VBox panelTotal = new VBox(6, new Label("Total a cobrar"), total, new Label("Cobrar · F5"));
        panelTotal.setPadding(new Insets(12));
        panelTotal.setMinWidth(200);

        VBox centro = new VBox(8, captura, renglones);
        VBox.setVgrow(renglones, Priority.ALWAYS);
        centro.setPadding(new Insets(12));

        Label atajos = new Label("F1 Buscar · F2 Cantidad · F3 Quitar · F4 Precuentas · F5 Cobrar · "
                + "F6 Descuento · F7 Cancelar · F8 Salida · F9 Corte");
        atajos.setPadding(new Insets(6, 12, 6, 12));

        BorderPane raiz = new BorderPane(centro, barraSuperior, panelTotal, atajos, null);
        Scene escena = new Scene(raiz, 1024, 640);

        // Toda la pantalla se opera con teclado (RNF-01)
        escena.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F1) captura.requestFocus();
        });

        ventana.setTitle("Sistema Veterinaria · Caja");
        ventana.setScene(escena);
        ventana.show();
        captura.requestFocus();
        verificarServidor();
    }

    /** Prueba de humo: llama a /api/salud sin congelar la pantalla. */
    private void verificarServidor() {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(SERVIDOR + "/api/salud")).GET().build();
        cliente.sendAsync(peticion, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> Platform.runLater(() -> estadoServidor.setText(
                        r.statusCode() == 200 ? "Servidor: conectado" : "Servidor: error " + r.statusCode())))
                .exceptionally(ex -> {
                    Platform.runLater(() -> estadoServidor.setText("Servidor: sin conexión"));
                    return null;
                });
    }

    private static HBox espacio() {
        HBox h = new HBox();
        HBox.setHgrow(h, Priority.ALWAYS);
        return h;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
