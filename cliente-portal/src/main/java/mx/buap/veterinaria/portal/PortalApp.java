package mx.buap.veterinaria.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Path;
import java.nio.file.Files;

/** Punto de entrada único; el CookieManager conserva JSESSIONID durante toda la sesión. */
public class PortalApp extends Application {
    private static final String SERVER = System.getProperty("servidor", "http://localhost:8080");
    private final ObjectMapper json = new ObjectMapper();
    private HttpClient http;
    private Stage stage;
    private CookieManager cookies;
    private Process cliente;
    private final Label error = new Label();

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        Platform.setImplicitExit(false);
        stage.setOnCloseRequest(e -> {
            if (cliente != null && cliente.isAlive()) { e.consume(); return; }
            Platform.exit();
        });
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        CookieHandler.setDefault(cookies);
        http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(4)).build();
        showLogin();
    }

    private void showLogin() {
        error.setText("");
        stage.setMaximized(false);
        TextField usuario = new TextField(); usuario.setPromptText("Usuario");
        PasswordField contrasena = new PasswordField(); contrasena.setPromptText("Contraseña");
        Button entrar = new Button("Iniciar sesión"); entrar.setDefaultButton(true);
        error.setStyle("-fx-text-fill:#B3261E;");
        entrar.setOnAction(e -> login(usuario.getText(), contrasena.getText(), entrar));
        entrar.getStyleClass().add("primary");
        entrar.setMaxWidth(Double.MAX_VALUE);
        error.setWrapText(true);
        Label hint = new Label("Ingrese sus datos para acceder a su área de trabajo.");
        hint.setWrapText(true); hint.getStyleClass().add("muted");
        VBox box = new VBox(12, title("Bienvenido a VetCQS"), hint,
                new Label("Usuario"), usuario, new Label("Contraseña"), contrasena, entrar, error);
        box.getStyleClass().add("card"); box.setMaxWidth(390); box.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane content = new StackPane(box); content.setPadding(new Insets(28));
        BorderPane root = new BorderPane(content, header("Acceso al sistema", null), null, null, null);
        root.setStyle("-fx-background-color: #101B2E;");
        stage.setTitle("Sistema Veterinaria · Portal");
        stage.setMinWidth(500); stage.setMinHeight(540);
        stage.setScene(scene(root, 580, 580)); stage.show();
    }

    private Label title(String text) { Label l = new Label(text); l.getStyleClass().add("page-title"); return l; }

    static Scene scene(javafx.scene.Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(PortalApp.class.getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        return scene;
    }

    static void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(PortalApp.class.getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
    }

    static HBox header(String subtitle, javafx.scene.Node action) {
        Label brand = new Label("SISTEMA VETERINARIA"); brand.getStyleClass().add("brand");
        Label detail = new Label(subtitle); detail.getStyleClass().add("subtitle");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(20, new VBox(2, brand, detail), spacer);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        if (action != null) header.getChildren().add(action);
        header.getStyleClass().add("header"); return header;
    }

    private void login(String user, String password, Button button) {
        if (user.isBlank() || password.isBlank()) { error.setText("Capture usuario y contraseña."); return; }
        error.setText("");
        button.setDisable(true);
        request("POST", "/api/auth/login", "{\"usuario\":\"" + escape(user) + "\",\"contrasena\":\"" + escape(password) + "\"}")
            .thenAccept(response -> Platform.runLater(() -> {
                button.setDisable(false);
                if (response.statusCode() == 200) {
                    try { route(json.readTree(response.body())); }
                    catch (Exception ex) { error.setText("Respuesta inválida del servidor."); }
                } else error.setText(response.statusCode() == 401 ? "Credenciales inválidas." : "Servidor no disponible.");
            }));
    }

    private void route(JsonNode session) {
        switch (session.path("rol").asText()) {
            case "ADMINISTRADOR" -> new AdminView(http, SERVER, stage, session, this::logout).show();
            case "CAJERO" -> lanzarCliente("cliente-caja");
            case "VETERINARIO" -> lanzarCliente("cliente-consultorio");
            default -> { cookies.getCookieStore().removeAll(); error.setText("El rol de esta cuenta no está habilitado."); }
        }
    }

    private void lanzarCliente(String modulo) {
        try {
            String cookie = cookies.getCookieStore().getCookies().stream()
                    .filter(c -> "JSESSIONID".equalsIgnoreCase(c.getName()))
                    .map(c -> c.getName() + "=" + c.getValue()).findFirst().orElse("");
            Path raiz = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            if (!Files.isDirectory(raiz.resolve(modulo))) raiz = raiz.getParent();
            boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
            Path maven = Path.of(System.getProperty("user.home"), "Tools", "apache-maven-3.10.0", "bin", windows ? "mvn.cmd" : "mvn");
            String ejecutable = Files.exists(maven) ? maven.toString() : (windows ? "mvn.cmd" : "mvn");
            ProcessBuilder proceso = new ProcessBuilder(ejecutable, "javafx:run", "-Dservidor=" + SERVER,
                    "-Dsesion=" + cookie);
            proceso.directory(raiz.resolve(modulo).toFile());
            cliente = iniciarProceso(proceso);
            stage.hide();
            // El portal permanece vivo para recuperar el login cuando el área de trabajo termina.
            cliente.onExit().thenAccept(terminado -> Platform.runLater(() -> {
                cliente = null;
                logout(terminado.exitValue() == 0 ? null : "No se pudo mantener abierta el área de trabajo. Verifique Java 21 y Maven.");
            }));
        } catch (Exception ex) {
            error.setText("No se pudo abrir el área de trabajo. Verifique Java 21 y Maven.");
            stage.show();
        }
    }

    protected Process iniciarProceso(ProcessBuilder proceso) throws java.io.IOException {
        return proceso.inheritIO().start();
    }

    private void logout() {
        logout(null);
    }

    private void logout(String mensaje) {
        stage.getScene().getRoot().setDisable(true);
        request("POST", "/api/auth/logout", "").thenAccept(response -> Platform.runLater(() -> {
            cookies.getCookieStore().removeAll();
            showLogin();
            if (response.statusCode() >= 400) error.setText("Sesión local cerrada. No se pudo contactar al servidor.");
            else if (mensaje != null) error.setText(mensaje);
        }));
    }
    private CompletableFuture<HttpResponse<String>> request(String method, String path, String body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SERVER + path))
                    .timeout(Duration.ofSeconds(8)).header("Content-Type","application/json");
            return http.sendAsync(method.equals("GET") ? b.GET().build() : b.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
                    .exceptionally(ignored -> HttpResponseStub.error());
        } catch (Exception e) { error.setText("No se pudo conectar al servidor."); return CompletableFuture.completedFuture(HttpResponseStub.error()); }
    }
    private static String escape(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
    private static final class HttpResponseStub { static HttpResponse<String> error() { return new HttpResponse<>() {
        public int statusCode(){return 503;} public HttpRequest request(){return null;} public Optional<HttpResponse<String>> previousResponse(){return Optional.empty();}
        public HttpHeaders headers(){return HttpHeaders.of(java.util.Map.of(),(a,b)->true);} public String body(){return "";}
        public Optional<SSLSession> sslSession(){return Optional.empty();} public URI uri(){return URI.create("http://localhost");} public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
    }; } }
}
