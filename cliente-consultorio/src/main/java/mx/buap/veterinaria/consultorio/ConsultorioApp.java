package mx.buap.veterinaria.consultorio;

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
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Esqueleto de la pantalla de consultorio (prototipo aprobado en la semana 3).
 * En la iteración 2 (semana 5) se conectan dueños, mascotas, consultas y el envío de la precuenta.
 */
public class ConsultorioApp extends Application {

    static final String SERVIDOR = System.getProperty("servidor", "http://localhost:8080");

    private final Label estadoServidor = new Label("Servidor: verificando…");

    @Override
    public void start(Stage ventana) {
        Label encabezado = new Label("Consultorio · Médico veterinario");
        HBox espacio = new HBox();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barraSuperior = new HBox(16, encabezado, espacio, estadoServidor);
        barraSuperior.setPadding(new Insets(8, 12, 8, 12));
        barraSuperior.setStyle("-fx-background-color: #C9CFD7;");

        TextField buscarDuenio = new TextField();
        buscarDuenio.setPromptText("Buscar dueño por nombre o teléfono");
        ListView<String> mascotas = new ListView<>();
        ListView<String> historial = new ListView<>();
        VBox paciente = new VBox(6, new Label("Dueño"), buscarDuenio, new Label("Mascotas"), mascotas,
                new Label("Historial"), historial);
        paciente.setPadding(new Insets(12));
        paciente.setPrefWidth(300);

        GridPane consulta = new GridPane();
        consulta.setHgap(10);
        consulta.setVgap(6);
        consulta.setPadding(new Insets(12));
        consulta.addRow(0, new Label("Motivo"), new TextField());
        consulta.addRow(1, new Label("Peso (kg)"), new TextField());
        consulta.addRow(2, new Label("Temperatura (°C)"), new TextField());
        consulta.addRow(3, new Label("Diagnóstico y tratamiento"), new TextArea());
        consulta.addRow(4, new Label("Precuenta"), new ListView<String>());
        consulta.add(new Label("Enviar a caja · F10"), 1, 5);

        BorderPane raiz = new BorderPane(consulta, barraSuperior, null, null, paciente);
        ventana.setTitle("Sistema Veterinaria · Consultorio");
        ventana.setScene(new Scene(raiz, 1024, 640));
        ventana.show();
        verificarServidor();
    }

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

    public static void main(String[] args) {
        launch(args);
    }
}
