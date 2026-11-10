package mx.buap.veterinaria.consultorio;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class ConsultorioApp extends Application {
    static final String SERVIDOR = System.getProperty("servidor", "http://localhost:8080");
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Label estado = new Label("Servidor: verificando...");
    private final Label aviso = new Label();
    private final Label paciente = new Label("Seleccione un dueño y su mascota.");
    private final Label datosDueno = new Label();
    private final Label datosMascota = new Label();
    private final ListView<JsonNode> duenos = new ListView<>();
    private final ListView<JsonNode> mascotas = new ListView<>();
    private final ListView<JsonNode> historial = new ListView<>();
    private final TableView<String> detalles = new TableView<>();
    private final ComboBox<JsonNode> selectorProducto = new ComboBox<>();
    private final TextField buscar = new TextField();
    private final TextField motivo = new TextField();
    private final TextField peso = new TextField();
    private final TextField temperatura = new TextField();
    private final TextArea diagnostico = new TextArea();
    private final TextArea tratamiento = new TextArea();
    private final java.util.Map<Integer, JsonNode> catalogoPrecuenta = new java.util.HashMap<>();
    private final TextField productoId = new TextField();
    private final TextField cantidad = new TextField("1");
    private final Button guardar = new Button("Guardar consulta · Ctrl+S");
    private final Button nuevaMascota = new Button("Registrar mascota");
    private Stage ventanaPrincipal;
    private JsonNode duenoSeleccionado;
    private JsonNode mascotaSeleccionada;
    private JsonNode consultaSeleccionada;
    private int busquedaVersion;
    private int mascotasVersion;
    private int historialVersion;
    private boolean cerrandoSesion;

    public ConsultorioApp() {
        String sesion = System.getProperty("sesion", "");
        if (!sesion.isBlank()) {
            try {
                HttpCookie cookie = HttpCookie.parse(sesion).get(0);
                cookie.setPath("/"); cookie.setVersion(0);
                cookies.getCookieStore().add(URI.create(SERVIDOR), cookie);
            }
            catch (IllegalArgumentException ignored) { }
        }
        http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(4)).build();
    }

    @Override public void start(Stage ventana) {
        ventanaPrincipal = ventana;
        Label marca = new Label("SISTEMA VETERINARIA"); marca.getStyleClass().add("brand");
        Label subtitulo = new Label("Atención médica  /  Consultorio"); subtitulo.getStyleClass().add("subtitle");
        estado.getStyleClass().add("connection-status");
        Button inventario = new Button("Revisar inventario · F4"); inventario.setOnAction(e -> revisarInventario());
        Button salir = new Button("Cerrar sesión"); salir.getStyleClass().add("logout"); salir.setOnAction(e -> cerrarSesion());
        Region espacio = new Region(); HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, new VBox(2, marca, subtitulo), espacio, estado, inventario, salir);
        barra.setAlignment(javafx.geometry.Pos.CENTER_LEFT); barra.getStyleClass().add("header");

        buscar.setId("buscar-dueno"); buscar.setPromptText("Nombre o teléfono"); buscar.setMinWidth(0);
        Button buscarBtn = new Button("Buscar · F2"); buscarBtn.setMinWidth(Region.USE_PREF_SIZE);
        buscarBtn.setOnAction(e -> buscarDuenos()); buscar.setOnAction(e -> buscarDuenos());
        HBox.setHgrow(buscar, Priority.ALWAYS);
        Button nuevoDueno = new Button("Registrar dueño"); nuevoDueno.setOnAction(e -> registrarDueno());
        nuevaMascota.setOnAction(e -> registrarMascota()); nuevaMascota.setDisable(true);
        duenos.setId("duenos"); mascotas.setId("mascotas"); historial.setId("historial");
        duenos.setPlaceholder(new Label("Registre un dueño o busque por nombre."));
        mascotas.setPlaceholder(new Label("Seleccione un dueño para ver sus mascotas."));
        historial.setPlaceholder(new Label("Seleccione un paciente para ver su historial."));
        duenos.setCellFactory(v -> textoCelda(n -> texto(n,"nombre") + " · " + texto(n,"telefono")));
        mascotas.setCellFactory(v -> textoCelda(n -> texto(n,"nombre") + " · " + texto(n,"especie")));
        historial.setCellFactory(v -> textoCelda(n -> fecha(texto(n,"fecha")) + " · " + texto(n,"motivo")));
        duenos.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> seleccionarDueno(b));
        mascotas.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> seleccionarMascota(b));
        historial.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> consultaSeleccionada = b);
        historial.setOnMouseClicked(e -> { if (e.getClickCount() == 2) verConsulta(); });
        Button ver = new Button("Ver consulta"); ver.setOnAction(e -> verConsulta());
        ver.disableProperty().bind(historial.getSelectionModel().selectedItemProperty().isNull());
        for (Label label : new Label[]{datosDueno, datosMascota, paciente, aviso}) { label.setWrapText(true); label.setMinHeight(Region.USE_PREF_SIZE); label.setMaxWidth(Double.MAX_VALUE); label.getStyleClass().add("muted"); }
        VBox panelDuenos = panel("DUEÑOS", new HBox(8, buscar, buscarBtn), nuevoDueno, duenos, datosDueno);
        VBox panelMascotas = panel("MASCOTAS", nuevaMascota, mascotas, datosMascota);
        VBox panelHistorial = panel("HISTORIAL CLÍNICO", historial, ver);

        motivo.setId("motivo"); peso.setId("peso"); temperatura.setId("temperatura");
        diagnostico.setId("diagnostico"); tratamiento.setId("tratamiento");
        diagnostico.setWrapText(true); tratamiento.setWrapText(true);
        diagnostico.setPrefRowCount(5); tratamiento.setPrefRowCount(5);
        motivo.setPromptText("Motivo de la visita (obligatorio)"); peso.setPromptText("Ej. 4.50"); temperatura.setPromptText("Ej. 38.5");
        GridPane formulario = grid(new String[]{"Motivo", "Peso (kg)", "Temperatura (°C)", "Diagnóstico", "Tratamiento"}, motivo, peso, temperatura, diagnostico, tratamiento);
        guardar.getStyleClass().add("primary"); guardar.setMaxWidth(Double.MAX_VALUE); guardar.setDisable(true);
        guardar.setOnAction(e -> guardarConsulta());
        Button nueva = new Button("Nueva consulta"); nueva.setOnAction(e -> limpiarConsulta());
        VBox consulta = panel("NUEVA CONSULTA MÉDICA", paciente, formulario, guardar, nueva);

        productoId.setPromptText("ID de producto o servicio"); cantidad.setPrefColumnCount(4);
        Button agregar = new Button("Agregar"); agregar.setOnAction(e -> agregarDetalle());
        Button quitar = new Button("Quitar"); quitar.setOnAction(e -> detalles.getItems().remove(detalles.getSelectionModel().getSelectedItem()));
        Button enviar = new Button("Enviar precuenta a caja · F10"); enviar.getStyleClass().add("primary");
        enviar.setMaxWidth(Double.MAX_VALUE); enviar.setOnAction(e -> enviarPrecuenta(enviar));
        detalles.setPrefHeight(100); detalles.setMinHeight(80); historial.setMinHeight(100);
        detalles.setId("detalle-precuenta");
        TableColumn<String,String> unidadesCol=new TableColumn<>("Cant.");
        unidadesCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().split(" x ")[1]));
        TableColumn<String,String> nombreCol=new TableColumn<>("Servicio / insumo");
        nombreCol.setCellValueFactory(c -> {int id=Integer.parseInt(c.getValue().split(" x ")[0]);JsonNode p=catalogoPrecuenta.get(id);return new SimpleStringProperty(p==null?"Producto #"+id:p.path("nombre").asText());});
        TableColumn<String,String> importeCol=new TableColumn<>("Importe");
        importeCol.setCellValueFactory(c -> {String[] partes=c.getValue().split(" x ");JsonNode p=catalogoPrecuenta.get(Integer.parseInt(partes[0]));return new SimpleStringProperty(p==null?"—":"$"+p.path("precio").decimalValue().multiply(new BigDecimal(partes[1])).setScale(2,java.math.RoundingMode.HALF_UP));});
        nombreCol.setPrefWidth(170);detalles.getColumns().addAll(unidadesCol,nombreCol,importeCol);
        detalles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detalles.setPlaceholder(new Label("Seleccione un servicio o insumo y pulse Agregar."));
        selectorProducto.setPromptText("Seleccione un servicio o insumo");selectorProducto.setMaxWidth(Double.MAX_VALUE);
        selectorProducto.setCellFactory(v -> textoCelda(n -> n.path("nombre").asText()+" · $"+n.path("precio").asText()));
        selectorProducto.setButtonCell(textoCelda(n -> n.path("nombre").asText()));
        selectorProducto.valueProperty().addListener((o,a,b) -> {if(b!=null)productoId.setText(String.valueOf(id(b)));});

        VBox cuenta = panel("SERVICIOS E INSUMOS A COBRAR", grid(new String[]{"Servicio / insumo", "Cantidad"}, selectorProducto, cantidad),
                new HBox(8, agregar, quitar), detalles, enviar);
        cuenta.setMinHeight(Region.USE_PREF_SIZE);
        VBox izquierda = new VBox(14, panelDuenos, panelMascotas); izquierda.setPrefWidth(300);
        VBox derecha = new VBox(14, panelHistorial, cuenta); derecha.setPrefWidth(340); consulta.setPrefWidth(430);
        HBox cuerpo = new HBox(14, izquierda, consulta, derecha); cuerpo.setPadding(new Insets(18));
        for (Node n : new Node[]{izquierda, consulta, derecha}) HBox.setHgrow(n, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(cuerpo); scroll.setFitToWidth(true); scroll.setFitToHeight(true);
        Label atajos = new Label("F2  Buscar dueño     F4  Inventario     Ctrl+S  Guardar consulta     F10  Enviar precuenta a caja");
        atajos.getStyleClass().add("shortcut-bar");
        VBox pie = new VBox(4, aviso, atajos);
        BorderPane raiz = new BorderPane(scroll, barra, null, pie, null);
        BorderPane.setMargin(pie, new Insets(0, 20, 12, 20));
        Scene escena = new Scene(raiz, 1360, 880); escena.getStylesheets().add(tema());
        // Diseño final (semana 6): atajos de teclado del consultorio.
        escena.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.F2) { buscar.requestFocus(); buscar.selectAll(); e.consume(); }
            else if (e.getCode() == javafx.scene.input.KeyCode.F4) { revisarInventario(); e.consume(); }
            else if (e.getCode() == javafx.scene.input.KeyCode.F10) { if (!enviar.isDisabled()) enviar.fire(); e.consume(); }
            else if (e.isShortcutDown() && e.getCode() == javafx.scene.input.KeyCode.S) { if (!guardar.isDisabled()) guardar.fire(); e.consume(); }
        });
        ventana.setMinWidth(1120); ventana.setMinHeight(720);
        ventana.setTitle("Sistema Veterinaria · Consultorio"); ventana.setScene(escena); ventana.show();
        api("GET", "/api/salud", null, n -> {estado.setText("Servidor: conectado");estado.getStyleClass().add("connected");}, e -> {estado.setText("Servidor: sin conexión");estado.getStyleClass().add("disconnected");});
        api("GET", "/api/productos", null, n -> {n.forEach(p -> {catalogoPrecuenta.put(id(p),p);selectorProducto.getItems().add(p);});detalles.refresh();}, this::error);
        buscarDuenos();
    }

    private void buscarDuenos() {
        int version = ++busquedaVersion;
        api("GET", "/api/clinica/duenos?q=" + encode(buscar.getText().trim()), null, n -> {
            if (version != busquedaVersion) return;
            duenos.getItems().clear(); n.forEach(duenos.getItems()::add);
            avisar(duenos.getItems().isEmpty() ? "No hay coincidencias. Puede registrar un nuevo dueño." : "Seleccione un dueño para ver sus mascotas.", false);
        }, this::error);
    }

    private void seleccionarDueno(JsonNode dueno) {
        duenoSeleccionado = dueno; int version = ++mascotasVersion;
        mascotas.getItems().clear(); seleccionarMascota(null);
        nuevaMascota.setDisable(dueno == null); datosDueno.setText(dueno == null ? "" :
                "Teléfono: " + texto(dueno,"telefono") + "\nCorreo: " + texto(dueno,"correo") + "\nDirección: " + texto(dueno,"direccion"));
        if (dueno == null) return;
        api("GET", "/api/clinica/duenos/" + id(dueno) + "/mascotas", null, n -> {
            if (version != mascotasVersion) return;
            n.forEach(mascotas.getItems()::add);
            mascotas.setPlaceholder(new Label("Este dueño aún no tiene mascotas registradas."));
        }, this::error);
    }

    private void seleccionarMascota(JsonNode mascota) {
        mascotaSeleccionada = mascota; historialVersion++;
        historial.getItems().clear(); consultaSeleccionada = null; detalles.getItems().clear(); limpiarConsulta();
        guardar.setDisable(mascota == null);
        datosMascota.setText(mascota == null ? "" : "Especie: " + texto(mascota,"especie") + " · Raza: " + texto(mascota,"raza")
                + "\nSexo: " + texto(mascota,"sexo") + " · Peso: " + texto(mascota,"pesoKg") + " kg");
        paciente.setText(mascota == null ? "Seleccione un dueño y su mascota." :
                "Paciente: " + texto(mascota,"nombre") + " (" + texto(mascota,"especie") + ")\nDueño: " + texto(duenoSeleccionado,"nombre"));
        if (mascota != null) cargarHistorial(id(mascota), 0);
    }

    private void cargarHistorial(int pacienteId, int seleccionarId) {
        int version = ++historialVersion;
        api("GET", "/api/clinica/mascotas/" + pacienteId + "/consultas", null, n -> {
            if (version != historialVersion || id(mascotaSeleccionada) != pacienteId) return;
            historial.getItems().clear(); n.forEach(historial.getItems()::add);
            historial.setPlaceholder(new Label("Este paciente aún no tiene consultas guardadas."));
            historial.getItems().stream().filter(c -> id(c) == seleccionarId).findFirst().ifPresent(historial.getSelectionModel()::select);
        }, this::error);
    }

    private void registrarDueno() {
        TextField nombre = new TextField(), telefono = new TextField(), correo = new TextField(), direccion = new TextField();
        Registro registro = registro("Registrar dueño", "Datos del dueño", new String[]{"Nombre *", "Teléfono", "Correo", "Dirección"}, nombre, telefono, correo, direccion);
        registro.guardar.setOnAction(e -> {
            try {
                ObjectNode body = json.createObjectNode().put("nombre", requerido(nombre, "Nombre", 120))
                        .put("telefono", limitado(telefono, "Teléfono", 20)).put("correo", limitado(correo, "Correo", 120))
                        .put("direccion", limitado(direccion, "Dirección", 200));
                if (!correo.getText().isBlank() && !correo.getText().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Revise el correo electrónico.");
                registro.guardar.setDisable(true);
                api("POST", "/api/clinica/duenos", body, creado -> {
                    registro.guardar.setDisable(false); registro.dialogo.close(); busquedaVersion++; buscar.setText(texto(creado,"nombre"));
                    duenos.getItems().setAll(creado); duenos.getSelectionModel().select(creado); avisar("Dueño registrado. Ya puede agregar sus mascotas.", false);
                }, registro::error);
            } catch (IllegalArgumentException ex) { registro.error(ex.getMessage()); }
        });
        registro.dialogo.showAndWait();
    }

    private void registrarMascota() {
        if (duenoSeleccionado == null) { error("Seleccione primero al dueño."); return; }
        int duenoId = id(duenoSeleccionado);
        TextField nombre = new TextField(), raza = new TextField(), pesoInicial = new TextField();
        ComboBox<String> especie = new ComboBox<>(FXCollections.observableArrayList("Perro", "Gato", "Pez", "Pájaro", "Otro")); especie.setValue("Perro");
        ComboBox<String> sexo = new ComboBox<>(FXCollections.observableArrayList("Sin especificar", "Macho", "Hembra")); sexo.setValue("Sin especificar");
        DatePicker nacimiento = new DatePicker(); nacimiento.setEditable(false);
        Registro registro = registro("Registrar mascota", "Dueño: " + texto(duenoSeleccionado,"nombre"),
                new String[]{"Nombre *", "Especie *", "Raza", "Sexo", "Nacimiento", "Peso (kg)"}, nombre, especie, raza, sexo, nacimiento, pesoInicial);
        registro.guardar.setOnAction(e -> {
            try {
                if (nacimiento.getValue() != null && nacimiento.getValue().isAfter(LocalDate.now())) throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura.");
                ObjectNode body = json.createObjectNode().put("nombre", requerido(nombre,"Nombre",60)).put("especie", especie.getValue())
                        .put("raza", limitado(raza,"Raza",60)).put("sexo", "Macho".equals(sexo.getValue()) ? "M" : "Hembra".equals(sexo.getValue()) ? "H" : null)
                        .put("fechaNacimiento", nacimiento.getValue() == null ? null : nacimiento.getValue().toString());
                body.put("pesoKg", decimal(pesoInicial, "Peso", 2, new BigDecimal("9999.99")));
                registro.guardar.setDisable(true);
                api("POST", "/api/clinica/duenos/" + duenoId + "/mascotas", body, creada -> {
                    registro.guardar.setDisable(false); registro.dialogo.close();
                    if (id(duenoSeleccionado) == duenoId) { mascotasVersion++; mascotas.getItems().add(creada); mascotas.getSelectionModel().select(creada); }
                    avisar("Mascota registrada y vinculada a su dueño.", false);
                }, registro::error);
            } catch (IllegalArgumentException ex) { registro.error(ex.getMessage()); }
        });
        registro.dialogo.showAndWait();
    }

    private void limpiarConsulta() {
        motivo.clear(); peso.clear(); temperatura.clear(); diagnostico.clear(); tratamiento.clear();
    }

    private void guardarConsulta() {
        if (mascotaSeleccionada == null) { error("Seleccione al paciente."); return; }
        try {
            int pacienteId = id(mascotaSeleccionada);
            ObjectNode body = json.createObjectNode().put("mascotaId", pacienteId).put("motivo", requerido(motivo,"Motivo",250))
                    .put("pesoKg", decimal(peso,"Peso",2,new BigDecimal("9999.99")))
                    .put("temperaturaC", decimal(temperatura,"Temperatura",1,new BigDecimal("999.9")))
                    .put("diagnostico", diagnostico.getText().trim()).put("tratamiento", tratamiento.getText().trim());
            guardar.setDisable(true);
            api("POST", "/api/clinica/consultas", body, guardada -> {
                guardar.setDisable(mascotaSeleccionada == null);
                if (id(mascotaSeleccionada) == pacienteId) { limpiarConsulta(); cargarHistorial(pacienteId, id(guardada)); }
                avisar("Consulta guardada en el historial del paciente y su dueño.", false);
            }, mensaje -> { guardar.setDisable(mascotaSeleccionada == null); error(mensaje); });
        } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
    }

    private void verConsulta() {
        if (consultaSeleccionada == null) return;
        JsonNode consulta = consultaSeleccionada;
        TextArea lectura = new TextArea("Paciente: " + texto(mascotaSeleccionada,"nombre") + "\nDueño: " + texto(duenoSeleccionado,"nombre")
                + "\nFecha: " + fecha(texto(consulta,"fecha")) + "\n\nMotivo: " + texto(consulta,"motivo")
                + "\nPeso: " + texto(consulta,"pesoKg") + " kg\nTemperatura: " + texto(consulta,"temperaturaC") + " °C"
                + "\n\nDiagnóstico\n" + texto(consulta,"diagnostico") + "\n\nTratamiento\n" + texto(consulta,"tratamiento"));
        lectura.setEditable(false); lectura.setWrapText(true); lectura.setPrefRowCount(18);
        Dialog<Void> dialogo = dialogo("Historial clínico", "Consulta del " + fecha(texto(consulta,"fecha")));
        dialogo.getDialogPane().setContent(lectura); dialogo.getDialogPane().setPrefWidth(650);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialogo.showAndWait();
    }

    private void revisarInventario() {
        Dialog<Void> dialogo = dialogo("Inventario", "Productos y servicios disponibles");
        TableView<JsonNode> tabla = new TableView<>(); tabla.setId("inventario");
        columna(tabla,"ID","id",55); columna(tabla,"Producto","nombre",240); columna(tabla,"Tipo","tipo",100);
        columna(tabla,"Precio ($)","precio",90); columna(tabla,"Existencias","stockActual",100);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Sin productos para esta búsqueda.")); tabla.setPrefHeight(340);
        TextField filtro = new TextField(); filtro.setPromptText("Buscar producto por nombre"); HBox.setHgrow(filtro, Priority.ALWAYS);
        Label resultado = new Label("Consultando inventario..."); resultado.setWrapText(true);
        Button buscarProducto = new Button("Buscar"); int[] version = {0};
        Runnable consultar = () -> {
            int actual = ++version[0]; resultado.setText("Consultando inventario...");
            api("GET", "/api/productos?q=" + encode(filtro.getText().trim()), null, n -> {
                if (actual != version[0] || !dialogo.isShowing()) return;
                tabla.getItems().clear(); n.forEach(tabla.getItems()::add);
                resultado.setText("Hasta 20 coincidencias. Refine la búsqueda para localizar un producto.");
            }, mensaje -> { if (actual == version[0]) { tabla.getItems().clear(); resultado.setText(mensaje); } });
        };
        buscarProducto.setOnAction(e -> consultar.run()); filtro.setOnAction(e -> consultar.run());
        Button usar = new Button("Usar en precuenta"); usar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        usar.setOnAction(e -> { JsonNode seleccionado=tabla.getSelectionModel().getSelectedItem(); catalogoPrecuenta.put(id(seleccionado),seleccionado); productoId.setText(String.valueOf(id(seleccionado))); agregarDetalle(); dialogo.close(); });
        dialogo.getDialogPane().setContent(new VBox(12, new HBox(8,filtro,buscarProducto), tabla, resultado, usar));
        dialogo.getDialogPane().setPrefWidth(760); dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogo.setOnShown(e -> consultar.run()); dialogo.showAndWait();
    }

    private void agregarDetalle() {
        try {
            int producto = Integer.parseInt(productoId.getText().trim());
            BigDecimal unidades = decimal(cantidad,"Cantidad",2,new BigDecimal("999999.99"));
            if (producto <= 0 || unidades == null || unidades.signum() <= 0) throw new IllegalArgumentException("Indique un producto y una cantidad positiva.");
            JsonNode item=catalogoPrecuenta.get(producto);
            if(item!=null && item.path("tipo").asText().equals("PRODUCTO")) {
                BigDecimal acumulada=detalles.getItems().stream().filter(x -> x.startsWith(producto+" x ")).map(x -> new BigDecimal(x.split(" x ")[1])).reduce(unidades,BigDecimal::add);
                if(acumulada.compareTo(item.path("stockActual").decimalValue())>0){error("La cantidad supera la existencia consultada.");return;}
            }
            detalles.getItems().add(producto + " x " + unidades.toPlainString()); productoId.clear(); selectorProducto.getSelectionModel().clearSelection();
        } catch (IllegalArgumentException ex) { error("Indique un ID de producto válido y una cantidad positiva (máximo 2 decimales)."); }
    }

    private void enviarPrecuenta(Button boton) {
        if (consultaSeleccionada == null) { error("Seleccione una consulta guardada del historial."); return; }
        if (detalles.getItems().isEmpty()) { error("Agregue un producto o servicio a la precuenta."); return; }
        int consultaId = id(consultaSeleccionada);
        ObjectNode body = json.createObjectNode().put("consultaId", consultaId); var lineas = body.putArray("detalles");
        for (String detalle : detalles.getItems()) { String[] partes = detalle.split(" x "); lineas.addObject().put("productoId",Integer.parseInt(partes[0])).put("cantidad",new BigDecimal(partes[1])); }
        boton.setDisable(true);
        api("POST", "/api/ventas/precuentas", body, n -> {
            boton.setDisable(false); if (id(consultaSeleccionada) == consultaId) detalles.getItems().clear(); avisar("Precuenta enviada a caja.",false);
        }, mensaje -> { boton.setDisable(false); error(mensaje); });
    }

    private void cerrarSesion() {
        cerrandoSesion = true; ventanaPrincipal.getScene().getRoot().setDisable(true);
        HttpRequest request = HttpRequest.newBuilder(URI.create(SERVIDOR + "/api/auth/logout"))
                .timeout(Duration.ofSeconds(8)).POST(HttpRequest.BodyPublishers.noBody()).build();
        http.sendAsync(request,HttpResponse.BodyHandlers.discarding()).whenComplete((r,e) -> Platform.runLater(() -> {
            cookies.getCookieStore().removeAll(); ventanaPrincipal.close();
        }));
    }

    private void api(String metodo, String ruta, JsonNode body, Consumer<JsonNode> success, Consumer<String> failure) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SERVIDOR + ruta)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json");
        HttpRequest request = "POST".equals(metodo) ? b.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build() : b.GET().build();
        http.sendAsync(request,HttpResponse.BodyHandlers.ofString()).whenComplete((r,ex) -> Platform.runLater(() -> {
            if (cerrandoSesion) return;
            if (ex != null) { failure.accept("No se pudo conectar al servidor. Intente nuevamente."); return; }
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                try { JsonNode err=json.readTree(r.body()); if(err.hasNonNull("message")){failure.accept(err.path("message").asText());return;} }catch(Exception ignored){}
                failure.accept(r.statusCode() == 401 || r.statusCode() == 403 ? "Su sesión no permite esta operación. Cierre sesión e ingrese nuevamente." :
                        "No se pudo guardar o consultar la información (" + r.statusCode() + "). Revise los datos e intente nuevamente."); return;
            }
            try { success.accept(json.readTree(r.body())); }
            catch (Exception err) { failure.accept("La respuesta del servidor no es válida."); }
        }));
    }

    private Registro registro(String titulo, String encabezado, String[] etiquetas, Control... controles) {
        Dialog<Void> d = dialogo(titulo, encabezado); Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("error");
        Button guardar = new Button("Guardar"); guardar.getStyleClass().add("primary");
        d.getDialogPane().setContent(new VBox(12,grid(etiquetas,controles),error,guardar));
        d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL); d.getDialogPane().setPrefWidth(580);
        // Una petición enviada termina antes de permitir cerrar o volver a enviar el formulario.
        d.getDialogPane().lookupButton(ButtonType.CANCEL).disableProperty().bind(guardar.disabledProperty());
        d.setOnCloseRequest(e -> { if (guardar.isDisabled()) e.consume(); });
        return new Registro(d,guardar,error);
    }
    private record Registro(Dialog<Void> dialogo, Button guardar, Label aviso) {
        void error(String mensaje) { guardar.setDisable(false); aviso.setText(mensaje); }
    }
    private Dialog<Void> dialogo(String titulo, String encabezado) {
        Dialog<Void> d = new Dialog<>(); d.initOwner(ventanaPrincipal); d.setTitle(titulo); d.setHeaderText(encabezado);
        d.getDialogPane().getStylesheets().add(tema()); d.setResizable(true); return d;
    }
    private static GridPane grid(String[] etiquetas, Control... campos) {
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(10);
        ColumnConstraints label = new ColumnConstraints(115), field = new ColumnConstraints(); field.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(label,field);
        for (int i=0;i<campos.length;i++) { Label l=new Label(etiquetas[i]); l.setLabelFor(campos[i]); campos[i].setMaxWidth(Double.MAX_VALUE); grid.addRow(i,l,campos[i]); }
        return grid;
    }
    private static VBox panel(String titulo, Node... nodos) {
        Label label = new Label(titulo); label.getStyleClass().add("section-title");
        VBox box = new VBox(12,label); box.getStyleClass().add("card"); box.getChildren().addAll(nodos); VBox.setVgrow(box,Priority.ALWAYS);
        for (Node n:nodos) if (n instanceof ListView<?>) VBox.setVgrow(n,Priority.ALWAYS); return box;
    }
    private static ListCell<JsonNode> textoCelda(java.util.function.Function<JsonNode,String> texto) {
        return new ListCell<>() { @Override protected void updateItem(JsonNode item,boolean empty) { super.updateItem(item,empty); setText(empty || item==null ? null : texto.apply(item)); } };
    }
    private static void columna(TableView<JsonNode> tabla,String nombre,String key,int ancho) {
        TableColumn<JsonNode,String> c=new TableColumn<>(nombre); c.setPrefWidth(ancho); c.setCellValueFactory(v -> new SimpleStringProperty(texto(v.getValue(),key))); tabla.getColumns().add(c);
    }
    private static String requerido(TextField campo,String nombre,int max) { String v=limitado(campo,nombre,max); if(v.isBlank()) throw new IllegalArgumentException(nombre+" es obligatorio."); return v; }
    private static String limitado(TextField campo,String nombre,int max) { String v=campo.getText().trim(); if(v.length()>max) throw new IllegalArgumentException(nombre+": máximo "+max+" caracteres."); return v; }
    private static BigDecimal decimal(TextField campo,String nombre,int escala,BigDecimal max) {
        if(campo.getText().isBlank()) return null;
        try { BigDecimal n=new BigDecimal(campo.getText().trim().replace(',','.')); if(n.signum()<=0 || n.compareTo(max)>0 || n.stripTrailingZeros().scale()>escala) throw new NumberFormatException(); return n; }
        catch(NumberFormatException e) { throw new IllegalArgumentException(nombre+": use un número positivo, hasta "+max+" y "+escala+" decimales."); }
    }
    private static int id(JsonNode n) { return n==null ? 0 : n.path("id").asInt(); }
    private static String texto(JsonNode n,String key) { return n==null || n.path(key).isNull() ? "" : n.path(key).asText(""); }
    private static String fecha(String s) { try { return LocalDateTime.parse(s).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")); } catch(Exception e) { return s; } }
    private static String encode(String s) { return URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8); }
    private String tema() { return getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm(); }
    private void avisar(String mensaje,boolean error) { aviso.setText(mensaje); aviso.setStyle(error ? "-fx-text-fill:#B3261E;" : "-fx-text-fill:#21865B;"); }
    private void error(String mensaje) { avisar(mensaje,true); }
    public static void main(String[] args) { launch(args); }
}
