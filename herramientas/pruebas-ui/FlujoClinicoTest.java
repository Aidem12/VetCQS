import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import mx.buap.veterinaria.portal.PortalApp;
import mx.buap.veterinaria.consultorio.ConsultorioApp;
import mx.buap.veterinaria.caja.CajaApp;

/** Regresión UI con API aislada en memoria; no utiliza la base de datos de la veterinaria. */
public class FlujoClinicoTest {
    static final ObjectMapper json=new ObjectMapper();
    static final List<JsonNode> owners=new CopyOnWriteArrayList<>(), pets=new CopyOnWriteArrayList<>(), records=new CopyOnWriteArrayList<>();
    static final Set<String> sessions=ConcurrentHashMap.newKeySet();
    static Stage portalStage, workStage;
    static volatile int logouts=0;
    static volatile String requestedModule;
    static volatile boolean failOwners;
    static HttpServer server;

    public static void main(String[] args) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", x -> {
            try {
                String path=x.getRequestURI().getPath(), method=x.getRequestMethod(); int code=200;
                JsonNode result=json.createArrayNode();
                String cookie=Optional.ofNullable(x.getRequestHeaders().getFirst("Cookie")).orElse("").replace("\"", "");
                JsonNode body=method.equals("POST") ? json.readTree(x.getRequestBody()) : null;
                if(path.equals("/api/auth/login")) {
                    String role=body.path("usuario").asText(); String token="JSESSIONID="+UUID.randomUUID(); sessions.add(token);
                    x.getResponseHeaders().add("Set-Cookie",token+"; Path=/; HttpOnly");
                    result=json.createObjectNode().put("id",42).put("rol",role).put("nombre","Prueba UI");
                } else if(path.equals("/api/auth/logout")) {
                    sessions.removeIf(cookie::contains); logouts++; code=204;
                } else if(path.equals("/api/salud")) { result=json.createObjectNode().put("estado","OK"); }
                else if(sessions.stream().noneMatch(cookie::contains)) { code=403; }
                else if(path.equals("/api/clinica/duenos")) {
                    if(method.equals("POST")) { ((ObjectNode)body).put("id",owners.size()+1); owners.add(body); result=body; code=201; }
                    else if(failOwners) { code=503; } else result=json.valueToTree(owners);
                } else if(path.matches("/api/clinica/duenos/\\d+/mascotas")) {
                    int owner=Integer.parseInt(path.split("/")[4]);
                    if(method.equals("POST")) { ((ObjectNode)body).put("id",pets.size()+1).put("duenoId",owner); pets.add(body); result=body; code=201; }
                    else result=json.valueToTree(pets.stream().filter(p -> p.path("duenoId").asInt()==owner).toList());
                } else if(path.equals("/api/clinica/consultas") && method.equals("POST")) {
                    ((ObjectNode)body).put("id",records.size()+1).put("fecha","2026-11-03T10:30:00"); records.add(body); result=body; code=201;
                } else if(path.matches("/api/clinica/mascotas/\\d+/consultas")) {
                    int pet=Integer.parseInt(path.split("/")[4]); result=json.valueToTree(records.stream().filter(c -> c.path("mascotaId").asInt()==pet).toList());
                } else if(path.equals("/api/productos")) {
                    result=json.readTree("[{\"id\":10,\"nombre\":\"Vacuna\",\"tipo\":\"PRODUCTO\",\"stockActual\":8,\"precio\":280}]");
                }
                byte[] bytes=json.writeValueAsBytes(result); x.getResponseHeaders().add("Content-Type","application/json");
                x.sendResponseHeaders(code,code==204 ? -1 : bytes.length); if(code!=204) x.getResponseBody().write(bytes); x.close();
            } catch(Exception e) { e.printStackTrace(); x.close(); }
        });
        server.start(); System.setProperty("servidor","http://127.0.0.1:"+server.getAddress().getPort());
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> { portalStage=new Stage(); new TestPortal().start(portalStage); return null; });
            login("ADMINISTRADOR"); waitFor(() -> fxUnchecked(() -> portalStage.getTitle().contains("Administración")));
            click(portalStage,"Cerrar sesión"); waitLogin(); check(sessions.isEmpty(),"Cookie administrativa invalidada");
            login("CAJERO"); waitFor(() -> workStage!=null && fxUnchecked(() -> workStage.isShowing()));
            check("cliente-caja".equals(requestedModule),"Entrada directa a Caja");
            click(workStage,"Cerrar sesión"); waitLogin(); check(sessions.isEmpty(),"Sesión de caja invalidada");
            workStage=null;
            login("VETERINARIO"); waitFor(() -> workStage!=null && fxUnchecked(() -> workStage.isShowing()));
            check("cliente-consultorio".equals(requestedModule),"Entrada directa a Consultorio");
            click(workStage,"Registrar dueño"); Stage d=dialog("Registrar dueño");
            click(d,"Guardar"); check(owners.isEmpty(),"No guarda un dueño vacío");
            field(d,"Nombre *","Ana Pérez"); field(d,"Teléfono","2221234567"); field(d,"Correo","ana@example.com"); field(d,"Dirección","Centro");
            Stage ownerDialog=d;
            click(d,"Guardar");
            waitFor(() -> owners.size()==1 && !fxUnchecked(ownerDialog::isShowing));
            check(owners.getFirst().path("correo").asText().equals("ana@example.com"),"Datos del dueño enviados");
            click(workStage,"Registrar mascota"); d=dialog("Registrar mascota"); Stage petDialog=d;
            field(d,"Nombre *","Luna"); field(d,"Raza","Mestiza"); field(d,"Peso (kg)","4.25");
            fx(() -> { ComboBox<String> species=(ComboBox<String>)control(petDialog,"Especie *"); check(species.getItems().containsAll(List.of("Perro","Gato","Pez","Pájaro")),"Especies disponibles"); species.setValue("Gato"); return null; });
            click(d,"Guardar"); waitFor(() -> pets.size()==1 && !fxUnchecked(petDialog::isShowing));
            check(pets.getFirst().path("duenoId").asInt()==1,"Mascota vinculada al dueño");
            fx(() -> { text(workStage,"#motivo","Revisión anual"); text(workStage,"#peso","4.25"); text(workStage,"#temperatura","38.5");
                text(workStage,"#diagnostico","Paciente estable\nSin lesiones"); text(workStage,"#tratamiento","Control en 7 días"); return null; });
            click(workStage,"Guardar consulta"); waitFor(() -> records.size()==1 && fxUnchecked(() -> list("#historial").getItems().size()==1));
            check(records.getFirst().path("mascotaId").asInt()==1,"Consulta vinculada al paciente");
            check(records.getFirst().path("diagnostico").asText().contains("\n"),"Diagnóstico multilínea conservado");
            fx(() -> { captura(workStage); return null; });
            click(workStage,"Ver consulta"); Stage history=dialog("Historial clínico");
            check(fx(() -> ((TextArea)history.getScene().lookup(".text-area")).getText().contains("Ana Pérez")),"Historial muestra al dueño");
            check(fx(() -> ((TextArea)history.getScene().lookup(".text-area")).getText().contains("Control en 7 días")),"Historial muestra tratamiento guardado");
            fx(() -> { history.close(); return null; });
            click(workStage,"Revisar inventario"); Stage inventory=dialog("Inventario");
            waitFor(() -> fxUnchecked(() -> ((TableView<?>)inventory.getScene().lookup("#inventario")).getItems().size()==1));
            fx(() -> { inventory.close(); return null; });
            owners.add(json.createObjectNode().put("id",2).put("nombre","Otro dueño"));
            click(workStage,"Buscar"); waitFor(() -> fxUnchecked(() -> list("#duenos").getItems().size()==2));
            fx(() -> { list("#duenos").getSelectionModel().select(1); return null; });
            waitFor(() -> fxUnchecked(() -> list("#mascotas").getItems().isEmpty() && list("#historial").getItems().isEmpty()));
            check(fx(() -> ((TextField)workStage.getScene().lookup("#motivo")).getText().isEmpty()),"Cambio de dueño limpia el paciente anterior");
            failOwners=true; click(workStage,"Buscar"); waitFor(() -> fxUnchecked(() -> hasText(workStage,"No se pudo guardar o consultar")));
            failOwners=false; click(workStage,"Buscar"); waitFor(() -> fxUnchecked(() -> list("#duenos").getItems().size()==2 && !hasText(workStage,"No se pudo guardar o consultar")));
            click(workStage,"Cerrar sesión"); waitLogin(); check(sessions.isEmpty(),"Sesión veterinaria invalidada");
            check(logouts>=3,"Todos los roles solicitaron logout");
            System.out.println("UI_FLOW_OK: login directo, logout de 3 roles, dueño, mascota, consulta, historial, inventario y recuperación de errores.");
        } finally { server.stop(0); fx(() -> { for(Window w:List.copyOf(Window.getWindows())) w.hide(); return null; }); Platform.exit(); }
    }

    public static class TestPortal extends PortalApp {
        @Override protected Process iniciarProceso(ProcessBuilder builder) {
            requestedModule=builder.directory().getName();
            for(String option:builder.command()) if(option.startsWith("-Dsesion=")) System.setProperty("sesion",option.substring(9));
            TestProcess process=new TestProcess(); workStage=new Stage();
            if(requestedModule.equals("cliente-caja")) new CajaApp().start(workStage); else new ConsultorioApp().start(workStage);
            workStage.setOnHidden(e -> process.done.complete(process)); return process;
        }
    }
    static class TestProcess extends Process {
        CompletableFuture<Process> done=new CompletableFuture<>();
        public CompletableFuture<Process> onExit(){return done;}
        public java.io.OutputStream getOutputStream(){return java.io.OutputStream.nullOutputStream();}
        public java.io.InputStream getInputStream(){return java.io.InputStream.nullInputStream();}
        public java.io.InputStream getErrorStream(){return java.io.InputStream.nullInputStream();}
        public int waitFor() throws InterruptedException {try{done.get();return 0;}catch(ExecutionException e){throw new RuntimeException(e);}}
        public int exitValue(){if(!done.isDone())throw new IllegalThreadStateException();return 0;}
        public boolean isAlive(){return !done.isDone();}
        public void destroy(){done.complete(this);}
    }
    static void login(String role) throws Exception { fx(() -> {
        List<TextField> fields=portalStage.getScene().getRoot().lookupAll(".text-field").stream().map(n -> (TextField)n).toList();
        fields.stream().filter(f -> !(f instanceof PasswordField)).findFirst().orElseThrow().setText(role);
        fields.stream().filter(f -> f instanceof PasswordField).findFirst().orElseThrow().setText("prueba123"); return null;
    }); click(portalStage,"Iniciar sesión"); }
    static void waitLogin() throws Exception { waitFor(() -> fxUnchecked(() -> portalStage.isShowing() && portalStage.getTitle().contains("Portal") && hasButton(portalStage,"Iniciar sesión")) ); }
    static void click(Stage s,String text) throws Exception { Button b=fx(() -> button(s,text)); Platform.runLater(b::fire); Thread.sleep(100); }
    static Button button(Stage s,String text){return s.getScene().getRoot().lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals(text)).map(n -> (Button)n).findFirst().orElseThrow(() -> new AssertionError("Falta botón "+text));}
    static boolean hasButton(Stage s,String text){try{button(s,text);return true;}catch(AssertionError e){return false;}}
    static boolean hasText(Stage s,String text){return s.getScene().getRoot().lookupAll(".label").stream().anyMatch(n -> ((Label)n).getText().contains(text));}
    static Control control(Stage s,String label){return (Control)s.getScene().getRoot().lookupAll(".label").stream().filter(n -> ((Label)n).getText().equals(label)).map(n -> ((Label)n).getLabelFor()).filter(Objects::nonNull).findFirst().orElseThrow();}
    static void field(Stage s,String label,String value) throws Exception {fx(() -> {((TextField)control(s,label)).setText(value);return null;});}
    static void text(Stage s,String selector,String value){((TextInputControl)s.getScene().lookup(selector)).setText(value);}
    static ListView<JsonNode> list(String selector){return (ListView<JsonNode>)workStage.getScene().lookup(selector);}
    static Stage dialog(String title) throws Exception {waitFor(() -> fxUnchecked(() -> Window.getWindows().stream().anyMatch(w -> w instanceof Stage s && s.isShowing() && s.getTitle().equals(title))));return fx(() -> (Stage)Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle().equals(title)).findFirst().orElseThrow());}
    static <T>T fx(Callable<T> action)throws Exception{FutureTask<T> f=new FutureTask<>(action);Platform.runLater(f);return f.get(15,TimeUnit.SECONDS);}
    static boolean fxUnchecked(Callable<Boolean> action){try{return fx(action);}catch(Exception e){throw new RuntimeException(e);}}
    static void waitFor(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("Tiempo agotado esperando la UI");Thread.sleep(50);}}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void captura(Stage stage) throws Exception {
        String destino=System.getProperty("captura"); if(destino==null)return;
        stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
        var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();
        int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
        var buffered=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);buffered.setRGB(0,0,w,h,pixels,0,w);
        javax.imageio.ImageIO.write(buffered,"png",new java.io.File(destino));
    }
}
