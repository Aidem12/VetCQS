package mx.buap.veterinaria.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** UI administrativa básica; todas las mutaciones se ejecutan contra endpoints protegidos. */
final class AdminView {
    private final HttpClient http; private final String server; private final Stage stage; private final JsonNode session;
    private final ObjectMapper json = new ObjectMapper(); private final TableView<JsonNode> employees = new TableView<>(), products = new TableView<>();
    AdminView(HttpClient h,String s,Stage st,JsonNode session){http=h;server=s;stage=st;this.session=session;}
    void show() {
        Button refresh = new Button("Actualizar"); refresh.setOnAction(e -> load());
        Button addEmployee = new Button("Nuevo personal"); addEmployee.setOnAction(e -> employeeForm());
        Button addProduct = new Button("Nuevo producto"); addProduct.setOnAction(e -> productForm());
        employees.setPlaceholder(new Label("Sin personal")); products.setPlaceholder(new Label("Sin productos"));
        VBox left = new VBox(8,new Label("PERSONAL"),employees,addEmployee); VBox right = new VBox(8,new Label("INVENTARIO / PRODUCTOS"),products,addProduct);
        HBox.setHgrow(left,Priority.ALWAYS); HBox.setHgrow(right,Priority.ALWAYS); VBox.setVgrow(employees,Priority.ALWAYS); VBox.setVgrow(products,Priority.ALWAYS);
        BorderPane root = new BorderPane(new HBox(12,left,right)); root.setTop(new Label("Administración · " + session.path("nombre").asText())); root.setPadding(new Insets(18));
        stage.setTitle("Sistema Veterinaria · Administración"); stage.setScene(new Scene(root,1000,620)); stage.show(); load();
    }
    private void load(){ request("GET","/api/empleados","").thenAccept(r -> Platform.runLater(() -> fill(employees,r))); request("GET","/api/productos","").thenAccept(r -> Platform.runLater(() -> fill(products,r))); }
    private void fill(TableView<JsonNode> table,HttpResponse<String> r){if(r.statusCode()!=200)return; try {table.setItems(FXCollections.observableArrayList(json.readTree(r.body()))); table.setRowFactory(t -> { TableRow<JsonNode> row = new TableRow<>(); row.itemProperty().addListener((o,a,b)->row.setText(b==null?"":b.toString())); return row; });}catch(Exception ignored){}}
    private void employeeForm(){ TextInputDialog d=new TextInputDialog(); d.setHeaderText("nombre,usuario,contraseña,rol"); d.showAndWait().ifPresent(v->{String[] x=v.split(",",-1); if(x.length==4) request("POST","/api/empleados","{\"nombre\":\""+x[0]+"\",\"usuario\":\""+x[1]+"\",\"contrasena\":\""+x[2]+"\",\"rol\":\""+x[3]+"\"}").thenRun(this::load);});}
    private void productForm(){ TextInputDialog d=new TextInputDialog(); d.setHeaderText("nombre,tipo,precio,stock,stockMinimo"); d.showAndWait().ifPresent(v->{String[] x=v.split(",",-1); if(x.length==5) request("POST","/api/productos","{\"nombre\":\""+x[0]+"\",\"tipo\":\""+x[1]+"\",\"precio\":"+x[2]+",\"stockActual\":"+x[3]+",\"stockMinimo\":"+x[4]+",\"manejaCaducidad\":false}").thenRun(this::load);});}
    private java.util.concurrent.CompletableFuture<HttpResponse<String>> request(String m,String p,String body){HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(server+p)).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json"); return http.sendAsync(m.equals("GET")?b.GET().build():b.method(m,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
}
