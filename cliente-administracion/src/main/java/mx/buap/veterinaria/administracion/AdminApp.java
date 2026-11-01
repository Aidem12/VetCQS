package mx.buap.veterinaria.administracion;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Entry point documentado para despliegues que separen el módulo administrativo. */
public final class AdminApp extends Application {
    @Override public void start(Stage stage) {
        Label title = new Label("VetCQS · Administración");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#17324D;");
        Label guard = new Label("Acceso exclusivo para ADMINISTRADOR.\nInicie sesión desde cliente-portal para conservar JSESSIONID.");
        VBox root = new VBox(16, title, guard); root.setPadding(new Insets(36));
        stage.setTitle("Sistema Veterinaria · Administración");
        stage.setScene(new Scene(root, 620, 280)); stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
