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
        VBox box = new VBox(12, title("VetCQS · Portal"), usuario, contrasena, entrar, error);
        box.setPadding(new Insets(36)); box.setMaxWidth(360);
        StackPane root = new StackPane(box); root.setStyle("-fx-background-color:#F3F6F9;");
        stage.setTitle("Sistema Veterinaria · Portal"); stage.setScene(new Scene(root, 520, 360)); stage.show();
    }

    private Label title(String text) { Label l = new Label(text); l.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#17324D;"); return l; }

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
        if ("ADMINISTRADOR".equals(role)) new AdminView(http, SERVER, stage, session).show();
        String modulo = "CAJERO".equals(role) ? "cliente-caja" : "cliente-consultorio";
        Button abrir = new Button("Abrir " + ("CAJERO".equals(role) ? "Caja" : "Consultorio"));
        abrir.setOnAction(e -> lanzarCliente(modulo, session));
        Button logout = new Button("Cerrar sesión"); logout.setOnAction(e -> logout());
        Label destination = new Label("Sesión iniciada como " + role
                + "\nEl área autorizada está lista para abrir.");
        destination.setStyle("-fx-font-size:18px;-fx-text-alignment:center;");
        VBox box = new VBox(18, title("Portal"), destination, abrir, logout);
        box.setPadding(new Insets(40));
        stage.setScene(new Scene(new StackPane(box), 560, 320));
    }

    private void lanzarCliente(String modulo, JsonNode session) {
        try {
            String cookie = CookieHandler.getDefault() instanceof CookieManager manager
                    ? manager.getCookieStore().getCookies().stream()
                            .filter(c -> "JSESSIONID".equalsIgnoreCase(c.getName()))
                            .map(c -> c.getName() + "=" + c.getValue()).findFirst().orElse("")
                    : "";
            Path raiz = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
            ProcessBuilder proceso = new ProcessBuilder("mvn.cmd", "javafx:run",
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
