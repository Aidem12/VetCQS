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
    private final Label error = new Label();

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        CookieHandler.setDefault(cookies);
        http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(4)).build();
        showLogin();
    }

    private void showLogin() {
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
        String role = session.path("rol").asText();
        if ("ADMINISTRADOR".equals(role)) { new AdminView(http, SERVER, stage, session).show(); return; }
        String modulo = "CAJERO".equals(role) ? "cliente-caja" : "cliente-consultorio";
        Button abrir = new Button("Abrir " + ("CAJERO".equals(role) ? "Caja" : "Consultorio"));
        abrir.setOnAction(e -> lanzarCliente(modulo, session));
        Button logout = new Button("Cerrar sesión"); logout.setOnAction(e -> logout());
        Label destination = new Label("Sesión iniciada como " + role
                + "\nEl área autorizada está lista para abrir.");
        destination.setWrapText(true);
        destination.getStyleClass().add("muted");
        abrir.getStyleClass().add("primary");
        abrir.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(18, title("Portal"), destination, abrir, logout);
        box.getStyleClass().add("card"); box.setMaxWidth(420); box.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane content = new StackPane(box); content.setPadding(new Insets(28));
        stage.setScene(scene(new BorderPane(content, header("Área de trabajo", null), null, null, null), 580, 540));
    }

    private void lanzarCliente(String modulo, JsonNode session) {
        try {
            String cookie = CookieHandler.getDefault() instanceof CookieManager manager
                    ? manager.getCookieStore().getCookies().stream()
                            .filter(c -> "JSESSIONID".equalsIgnoreCase(c.getName()))
                            .map(c -> c.getName() + "=" + c.getValue()).findFirst().orElse("")
                    : "";
            Path raiz = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
            Path maven = Path.of(System.getProperty("user.home"),
                    "Tools", "apache-maven-3.10.0", "bin", "mvn.cmd");
            String ejecutable = Files.exists(maven) ? maven.toString() : "mvn.cmd";
            ProcessBuilder proceso = new ProcessBuilder(ejecutable, "javafx:run",
                    "-Dservidor=" + SERVER, "-Dsesion=" + cookie);
            proceso.directory(raiz.resolve(modulo).toFile());
            proceso.inheritIO().start();
            stage.hide();
        } catch (Exception ex) {
            error.setText("No se pudo abrir el módulo autorizado: " + ex.getMessage());
        }
    }

    private void logout() {
        request("POST", "/api/auth/logout", "")
            .thenRun(() -> Platform.runLater(this::showLogin));
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
