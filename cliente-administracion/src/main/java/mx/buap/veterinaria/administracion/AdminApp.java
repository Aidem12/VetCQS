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
        Label brand = new Label("SISTEMA VETERINARIA"); brand.getStyleClass().add("brand");
        Label subtitle = new Label("Gestión  /  Administración"); subtitle.getStyleClass().add("subtitle");
        VBox header = new VBox(2, brand, subtitle); header.getStyleClass().addAll("header", "vertical-header");
        Label title = new Label("Acceso administrativo"); title.getStyleClass().add("page-title");
        Label guard = new Label("Inicie sesión desde el portal con una cuenta de administrador para acceder a la gestión de personal y productos.");
        guard.setWrapText(true); guard.getStyleClass().add("muted");
        VBox card = new VBox(12, title, guard); card.getStyleClass().add("card");
        card.setMaxWidth(480); card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        javafx.scene.layout.StackPane content = new javafx.scene.layout.StackPane(card); content.setPadding(new Insets(28));
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane(content, header, null, null, null);
        Scene scene = new Scene(root, 620, 380);
        scene.getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        stage.setTitle("Sistema Veterinaria · Administración");
        stage.setMinWidth(520); stage.setMinHeight(350);
        stage.setScene(scene); stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
