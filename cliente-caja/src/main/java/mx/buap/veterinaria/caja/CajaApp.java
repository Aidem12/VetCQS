package mx.buap.veterinaria.caja;

import java.math.BigDecimal;
import java.net.URI;
import java.net.HttpCookie;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class CajaApp extends Application {

    static final String SERVIDOR = System.getProperty("servidor", "http://localhost:8080");
    private static final String NOMBRE_NEGOCIO = "VetCQS";
    private static final String DIRECCION_SUCURSAL =
            "Sucursal Puebla 11 Sur\nAv 11 Sur 12914 GPE HIDALGO Puebla, PUE.";
    private static final String CAJA = "Caja 1";
    private String atendio = "Cajero";
    private static final DateTimeFormatter FORMATO_FECHA_TICKET =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final ObservableList<Renglon> carrito = FXCollections.observableArrayList();
    private final Label estadoServidor = new Label("Servidor: verificando...");
    private final Label total = new Label("$0.00");
    private final Label resumenCarrito = new Label("0 productos");
    private final TableView<Renglon> tabla = new TableView<>(carrito);
    private Stage ventanaPrincipal;
    private boolean servidorDisponible;
    private volatile int ventaCargada;
    private boolean cobroEnCurso;
    private String ultimoMetodo="Efectivo";
    private BigDecimal descuentoActual=BigDecimal.ZERO;
    private final Label clienteVenta = new Label("Venta de mostrador");
    private static final List<ProductoTemporal> PRODUCTOS_DE_PRUEBA = List.of(
            new ProductoTemporal(101, "7501000000011", "Croquetas adulto 2 kg", new BigDecimal("389.00"), new BigDecimal("2")),
            new ProductoTemporal(102, "7501000000028", "Desparasitante, tableta", new BigDecimal("45.00"), new BigDecimal("40")),
            new ProductoTemporal(103, "7501000000035", "Arena para gato 5 kg", new BigDecimal("159.00"), new BigDecimal("3")),
            new ProductoTemporal(104, "7501000000042", "Vacuna múltiple canina", new BigDecimal("280.00"), new BigDecimal("8")),
            new ProductoTemporal(105, null, "Consulta general", new BigDecimal("350.00"), BigDecimal.ZERO),
            new ProductoTemporal(106, null, "Aplicación de vacuna", new BigDecimal("120.00"), BigDecimal.ZERO));

    @Override
    public void start(Stage ventana) {
        // Conserva la cookie JSESSIONID entre login y llamadas protegidas.
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        java.net.CookieHandler.setDefault(cookies);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).cookieHandler(cookies).build();
        String sesion = System.getProperty("sesion", "");
        if (!sesion.isBlank()) {
            try {
                HttpCookie cookie = HttpCookie.parse(sesion).get(0);
                cookie.setPath("/"); cookie.setVersion(0);
                cookies.getCookieStore().add(URI.create(SERVIDOR), cookie);
            } catch (IllegalArgumentException ex) {
                estadoServidor.setText("Sesión inválida");
            }
        }
        ventanaPrincipal = ventana;
        Label marca = new Label("SISTEMA VETERINARIA");
        marca.getStyleClass().add("brand");
        Label encabezado = new Label("Punto de venta  /  Caja");
        encabezado.getStyleClass().add("subtitle");
        VBox identidad = new VBox(2, marca, encabezado);
        estadoServidor.getStyleClass().add("connection-status");
        Button cerrarSesion = new Button("Cerrar sesión"); cerrarSesion.getStyleClass().add("logout");
        cerrarSesion.setOnAction(e -> cerrarSesion());
        HBox barraSuperior = new HBox(20, identidad, espacio(), estadoServidor, cerrarSesion);
        barraSuperior.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        barraSuperior.getStyleClass().add("header");

        TextField captura = new TextField();
        captura.setPromptText("Escanee o escriba código de barras o nombre");
        captura.getStyleClass().add("search-field");
        captura.setOnAction(e -> buscar(captura));

        TableColumn<Renglon, BigDecimal> cantidad = new TableColumn<>("Cant.");
        cantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        cantidad.setEditable(true);
        cantidad.setOnEditCommit(e -> {
            if(ventaCargada!=0){tabla.refresh();return;}
            BigDecimal nuevaCantidad = e.getNewValue();
            if (nuevaCantidad != null && nuevaCantidad.compareTo(BigDecimal.ZERO) > 0) {
                e.getRowValue().setCantidad(nuevaCantidad);
                actualizarTotal();
                tabla.refresh();
            }

        });
        cantidad.setCellFactory(TextFieldTableCell.forTableColumn(new javafx.util.converter.BigDecimalStringConverter()));
        TableColumn<Renglon, String> nombre = new TableColumn<>("Producto");
        nombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        TableColumn<Renglon, BigDecimal> precio = new TableColumn<>("Precio");
        precio.setCellValueFactory(new PropertyValueFactory<>("precio"));
        precio.setCellFactory(columna -> celdaMonetaria());
        TableColumn<Renglon, BigDecimal> importe = new TableColumn<>("Importe");
        importe.setCellValueFactory(new PropertyValueFactory<>("importe"));
        importe.setCellFactory(columna -> celdaMonetaria());
        tabla.getColumns().addAll(cantidad, nombre, precio, importe);
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Aún no hay productos en la venta. Escanee o busque uno para comenzar."));
        precio.getStyleClass().add("money-column");
        importe.getStyleClass().addAll("money-column", "amount-column");
        cantidad.getStyleClass().add("center-column");
        cantidad.setMaxWidth(110);

        total.getStyleClass().add("total-amount");
        Button cobrar = new Button("Cobrar · F5");
        cobrar.setMaxWidth(Double.MAX_VALUE);
        cobrar.getStyleClass().addAll("primary", "big");
        cobrar.setOnAction(e -> cobrar());
        Button inventario = new Button("Revisar inventario");
        inventario.setMaxWidth(Double.MAX_VALUE);
        inventario.setOnAction(e -> revisarInventario());
        Label tituloTotal = new Label("RESUMEN DE VENTA · TOTAL A COBRAR");
        tituloTotal.getStyleClass().add("section-title");
        resumenCarrito.getStyleClass().add("muted");
        VBox panelTotal = new VBox(12, tituloTotal, total, resumenCarrito, cobrar, inventario);
        panelTotal.setMinWidth(260);
        panelTotal.getStyleClass().addAll("card", "total-panel");

        Button precuentas = new Button("F1 · Cargar precuenta médica"); precuentas.setOnAction(e -> cargarPrecuenta());
        Button agregarBtn = new Button("Agregar · Enter"); agregarBtn.getStyleClass().add("primary");agregarBtn.setOnAction(e -> buscar(captura));
        HBox.setHgrow(captura,Priority.ALWAYS);
        Button autorizar=new Button("Autorización administrativa");autorizar.setOnAction(e -> autorizarVenta());
        VBox centro = new VBox(12, new HBox(8,precuentas,autorizar), new HBox(8,captura,agregarBtn), tabla, clienteVenta);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        centro.setPadding(new Insets(18, 12, 12, 18));
        Label atajos = new Label("ENTER  Buscar y agregar     F1  Cargar precuenta     F5  Cobrar     SUPR  Eliminar producto seleccionado");
        atajos.getStyleClass().add("shortcut-bar");
        atajos.setPadding(new Insets(8, 18, 12, 18));

        BorderPane raiz = new BorderPane(centro, new VBox(barraSuperior, new TurnosView(http, ventana).acciones()), panelTotal, atajos, null);
        Scene escena = new Scene(raiz, 1200, 760);
        escena.getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        ventana.setMinWidth(1100);
        ventana.setMinHeight(560);
        BorderPane.setMargin(panelTotal, new Insets(18, 18, 12, 6));
        escena.setOnKeyPressed(e -> {
            if(e.getCode()==KeyCode.F1){cargarPrecuenta();e.consume();}
            if(e.getCode()==KeyCode.F5){cobrar();e.consume();}
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE && !(e.getTarget() instanceof javafx.scene.control.TextInputControl)) {
                eliminarSeleccionado();
            }
        });

        ventana.setTitle("Sistema Veterinaria · Caja");
        ventana.setScene(escena);
        ventana.show();
        captura.requestFocus();
        verificarServidor();
        request("GET","/api/auth/sesion",null).thenAccept(r -> Platform.runLater(() -> {
            if(r.statusCode()==200)try{atendio=json.readTree(r.body()).path("nombre").asText("Cajero");encabezado.setText("Punto de venta / Caja · "+atendio);}catch(Exception ignored){}
        }));
    }

    private void cerrarSesion() {
        ventanaPrincipal.getScene().getRoot().setDisable(true);
        request("POST", "/api/auth/logout", "").whenComplete((r, error) -> Platform.runLater(() -> {
            http.cookieHandler().filter(CookieManager.class::isInstance)
                    .map(CookieManager.class::cast).ifPresent(c -> c.getCookieStore().removeAll());
            carrito.clear();
            ventanaPrincipal.close();
        }));
    }

    private void buscar(TextField captura) {
        if(ventaCargada!=0){mostrarError("La venta guardada está lista para cobro. No se puede modificar desde la captura.");return;}
        String texto = captura.getText().trim();
        if (texto.isEmpty()) return;
        if (!servidorDisponible) {
            buscarProductoDePrueba(texto, captura);
            return;
        }
        String ruta = texto.matches("\\d+") ? "/api/productos/codigo/" + texto : "/api/productos?q=" + encode(texto);
        request("GET", ruta, null).thenAccept(respuesta -> Platform.runLater(() -> {
            try {
                JsonNode nodo = json.readTree(respuesta.body());
                if (respuesta.statusCode() == 200) {
                    JsonNode producto = nodo.isArray() ? (nodo.size() == 0 ? null : nodo.get(0)) : nodo;
                    if (producto == null) throw new IllegalArgumentException("No se encontró el producto");
                    agregar(producto);
                    captura.clear();
                } else {
                    mostrarError("No se encontró un producto activo");
                }
            } catch (Exception ex) {
                mostrarError(ex.getMessage());
            }
        }));
    }

    private void buscarProductoDePrueba(String texto, TextField captura) {
        String consulta = texto.toLowerCase(Locale.ROOT);
        ProductoTemporal producto = PRODUCTOS_DE_PRUEBA.stream()
                .filter(p -> texto.equals(p.codigo()) || p.nombre().toLowerCase(Locale.ROOT).contains(consulta))
                .findFirst()
                .orElse(null);
        if (producto == null) {
            mostrarError("No se encontró un producto en los datos temporales");
            return;
        }
        agregar(productoJson(producto));
        captura.clear();
    }

    private JsonNode productoJson(ProductoTemporal producto) {
        return json.createObjectNode()
                .put("id", producto.id())
                .put("codigoBarras", producto.codigo())
                .put("nombre", producto.nombre())
                .put("precio", producto.precio())
                .put("stockActual", producto.stock());
    }

    private void agregar(JsonNode producto) {
        int id = producto.get("id").asInt();
        BigDecimal precio = producto.get("precio").decimalValue();
        carrito.stream().filter(r -> r.id == id).findFirst().ifPresentOrElse(
                r -> r.cantidad = r.cantidad.add(BigDecimal.ONE),
                () -> carrito.add(new Renglon(id, producto.get("nombre").asText(), precio)));
        actualizarTotal();
        tabla.refresh();
    }

    private void cobrar() {
        if(cobroEnCurso)return;
        if (carrito.isEmpty()) {
            mostrarError("Agregue al menos un producto");
            return;
        }
        BigDecimal importe = total();
        javafx.scene.control.ComboBox<String> metodo = new javafx.scene.control.ComboBox<>(FXCollections.observableArrayList("Efectivo", "Tarjeta", "Transferencia", "Mixto"));
        metodo.setValue("Efectivo"); metodo.setMaxWidth(Double.MAX_VALUE);
        TextField parteTarjeta = new TextField("0.00"), parteTransferencia = new TextField("0.00");
        TextField recibido = new TextField(formatearDinero(importe));
        Label cambioVista = new Label("Cambio: $0.00");
        javafx.scene.layout.GridPane mezcla = new javafx.scene.layout.GridPane(); mezcla.setHgap(12);mezcla.setVgap(8);
        mezcla.addRow(0,new Label("Tarjeta ($)"),parteTarjeta);mezcla.addRow(1,new Label("Transferencia ($)"),parteTransferencia);
        mezcla.visibleProperty().bind(metodo.valueProperty().isEqualTo("Mixto"));mezcla.managedProperty().bind(mezcla.visibleProperty());
        recibido.disableProperty().bind(metodo.valueProperty().isEqualTo("Tarjeta").or(metodo.valueProperty().isEqualTo("Transferencia")));
        Runnable actualizarCambio = () -> {try {
            BigDecimal efectivoDebido = metodo.getValue().equals("Mixto") ? importe.subtract(new BigDecimal(parteTarjeta.getText())).subtract(new BigDecimal(parteTransferencia.getText())) : importe;
            BigDecimal cambio = recibido.isDisabled() ? BigDecimal.ZERO : new BigDecimal(recibido.getText()).subtract(efectivoDebido);
            cambioVista.setText(cambio.signum()<0 ? "Falta efectivo: $"+cambio.abs() : "Cambio: $"+cambio);
        } catch(Exception ex){cambioVista.setText("Revise los importes");}};
        recibido.textProperty().addListener((o,a,b) -> actualizarCambio.run());
        parteTarjeta.textProperty().addListener((o,a,b) -> actualizarCambio.run());
        parteTransferencia.textProperty().addListener((o,a,b) -> actualizarCambio.run());
        metodo.valueProperty().addListener((o,a,b) -> actualizarCambio.run());
        recibido.setPromptText("Efectivo recibido");
        VBox productos = new VBox(6);
        productos.setStyle("-fx-background-color: white; -fx-border-color: #D7E0E8; "
                + "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");
        for (Renglon renglon : carrito) {
            Label producto = new Label(
                    renglon.getCantidad().stripTrailingZeros().toPlainString() + " × "
                            + renglon.getNombre() + "    $" + formatearDinero(renglon.getImporte()));
            producto.setStyle("-fx-text-fill: #425466; -fx-font-size: 13px;");
            productos.getChildren().add(producto);
        }
        Label totalDialogo = new Label("Total a pagar: $" + formatearDinero(importe));
        totalDialogo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #17324D;");
        Label productosTitulo = new Label("PRODUCTOS DE LA VENTA");
        productosTitulo.setStyle("-fx-text-fill: #6B7C8F; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label franja = new Label("RESUMEN DE LA VENTA");
        franja.setMaxWidth(Double.MAX_VALUE);
        franja.setStyle("-fx-background-color: #17324D; -fx-text-fill: white; "
                + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 12px 14px;");
        Label recibidoLabel = new Label("EFECTIVO RECIBIDO");
        recibidoLabel.setStyle("-fx-text-fill: #6B7C8F; -fx-font-size: 11px; -fx-font-weight: bold;");
        recibido.setStyle("-fx-font-size: 14px; -fx-padding: 9px; -fx-background-color: white; "
                + "-fx-border-color: #B8C7D6; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        javafx.scene.control.CheckBox facturaDemo=new javafx.scene.control.CheckBox("Solicitar comprobante fiscal de demostración");
        TextField rfc=new TextField(), razon=new TextField(), regimen=new TextField(), postal=new TextField(), uso=new TextField("G03");
        VBox fiscal=new VBox(8,new Label("DATOS FISCALES · DEMOSTRACIÓN"),facturaDemo,new Label("RFC"),rfc,new Label("Razón social"),razon,new Label("Régimen fiscal"),regimen,new Label("Código postal"),postal,new Label("Uso CFDI"),uso);
        fiscal.getStyleClass().add("card");fiscal.setPrefWidth(330);
        for(TextField campo:java.util.List.of(rfc,razon,regimen,postal,uso))campo.disableProperty().bind(facturaDemo.selectedProperty().not());
        Label avisoFiscal=new Label("Sin timbrado SAT ni envío de correo. Comprobante únicamente de demostración.");avisoFiscal.setWrapText(true);fiscal.getChildren().add(avisoFiscal);
        VBox pagoPanel=new VBox(12,productosTitulo,productos,totalDialogo,new Label("Método de pago"),metodo,mezcla,recibidoLabel,recibido,cambioVista);pagoPanel.getStyleClass().add("card");pagoPanel.setPrefWidth(390);
        VBox contenido = new VBox(12, franja, new HBox(16,pagoPanel,fiscal));
        contenido.setPadding(new Insets(16));
        contenido.setStyle("-fx-background-color: #F3F6F9;");
        Alert dialogo = new Alert(Alert.AlertType.CONFIRMATION);
        dialogo.setTitle("Confirmar cobro");
        dialogo.setHeaderText(null);
        dialogo.setGraphic(null);
        prepararDialogo(dialogo);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().setStyle("-fx-background-color: #F3F6F9; "
                + "-fx-border-color: #D7E0E8; -fx-border-width: 1px;");
        dialogo.getDialogPane().setMinWidth(820);
        dialogo.getDialogPane().setMinHeight(390);
        dialogo.showAndWait().filter(r -> r.getButtonData().isDefaultButton()).ifPresent(r -> {
            try {
                if(facturaDemo.isSelected() && (rfc.getText().isBlank() || razon.getText().isBlank() || !regimen.getText().matches("\\d{3}") || !postal.getText().matches("\\d{5}") || uso.getText().isBlank())) {mostrarError("Complete correctamente los datos fiscales de demostración.");return;}
                BigDecimal tarjeta = metodo.getValue().equals("Tarjeta") ? importe : metodo.getValue().equals("Mixto") ? new BigDecimal(parteTarjeta.getText()) : BigDecimal.ZERO;
                BigDecimal transferencia = metodo.getValue().equals("Transferencia") ? importe : metodo.getValue().equals("Mixto") ? new BigDecimal(parteTransferencia.getText()) : BigDecimal.ZERO;
                BigDecimal montoEfectivo = importe.subtract(tarjeta).subtract(transferencia);
                if(tarjeta.signum()<0 || transferencia.signum()<0 || montoEfectivo.signum()<0 || tarjeta.scale()>2 || transferencia.scale()>2) {
                    mostrarError("Revise la distribución del pago. Los importes deben sumar el total.");return;
                }
                BigDecimal recibidoEfectivo = montoEfectivo.signum()==0 ? BigDecimal.ZERO : new BigDecimal(recibido.getText());
                BigDecimal efectivo = recibidoEfectivo.add(tarjeta).add(transferencia);
                if (recibidoEfectivo.scale()>2 || recibidoEfectivo.compareTo(montoEfectivo) < 0) {
                    mostrarError("Pago insuficiente",
                            "La cantidad recibida es menor al total a pagar.");
                    return;
                }
                if (!servidorDisponible) {
                    List<Renglon> venta = List.copyOf(carrito);
                    String folio = "LOCAL-" + System.currentTimeMillis();
                    carrito.clear();
                    actualizarTotal();
                    mostrarPedidoCompletado(importe, efectivo, folio);
                    mostrarTicket(venta, importe, efectivo, folio);
                    return;
                }
                String cuerpo = json.writeValueAsString(VentaRequest.desde(carrito, BigDecimal.ZERO));
                cobroEnCurso=true;ventanaPrincipal.getScene().getRoot().setDisable(true);ultimoMetodo=metodo.getValue();
                request(ventaCargada==0 ? "POST" : "GET", ventaCargada==0 ? "/api/ventas" : "/api/ventas/"+ventaCargada, cuerpo).whenComplete((creada, falloCreacion) -> {
                    if(falloCreacion!=null){Platform.runLater(() -> {finCobro();mostrarError("No se pudo preparar el cobro. Revise las ventas pendientes antes de reintentar.");});return;}
                    if (creada.statusCode() != 201 && creada.statusCode() != 200) {
                        Platform.runLater(() -> {finCobro();mostrarOperacionFallida(creada);});
                        return;
                    }
                    try {
                        int id = json.readTree(creada.body()).get("id").asInt();
                        ventaCargada=id;
                        var pagos = json.createArrayNode();
                        if(montoEfectivo.signum()>0) pagos.addObject().put("forma","EFECTIVO").put("monto",montoEfectivo).put("recibido",recibidoEfectivo);
                        if(tarjeta.signum()>0) pagos.addObject().put("forma","TARJETA").put("monto",tarjeta);
                        if(transferencia.signum()>0) pagos.addObject().put("forma","TRANSFERENCIA").put("monto",transferencia);
                        String pago = pagos.toString();
                        request("POST", "/api/ventas/" + id + "/cobro", pago).whenComplete((cobrada, falloCobro) ->
                                Platform.runLater(() -> {
                                    finCobro();
                                    if(falloCobro!=null){mostrarError("No se pudo confirmar el cobro. Consulte la venta antes de reintentar.");return;}
                                    if (cobrada.statusCode() == 200) {
                                        List<Renglon> venta = List.copyOf(carrito);
                                        ventaCargada=0; descuentoActual=BigDecimal.ZERO; clienteVenta.setText("Venta de mostrador");
                                        carrito.clear();
                                        actualizarTotal();
                                        mostrarPedidoCompletado(importe, efectivo, "V-" + id);
                                        mostrarTicket(venta, importe, efectivo, "V-" + id);
                                        if(facturaDemo.isSelected()) {
                                            var fiscalBody=json.createObjectNode().put("rfcReceptor",rfc.getText().trim()).put("razonSocial",razon.getText().trim()).put("regimenFiscal",regimen.getText()).put("codigoPostal",postal.getText()).put("usoCfdi",uso.getText()).put("idempotencia","demo-venta-"+id);
                                            request("POST","/api/facturas/venta/"+id,fiscalBody.toString()).whenComplete((f,fallo) -> Platform.runLater(() -> mostrarInfo(fallo==null && f.statusCode()<300 ? "Comprobante de demostración registrado. No tiene validez fiscal." : "La venta fue cobrada, pero no se pudo registrar el comprobante de demostración.")));
                                        }
                                    } else {
                                        mostrarOperacionFallida(cobrada);
                                    }
                                }));
                    } catch (Exception ex) {
                        Platform.runLater(() -> {finCobro();mostrarError(ex.getMessage());});
                    }
                });
            } catch (NumberFormatException | com.fasterxml.jackson.core.JsonProcessingException ex) {
                mostrarError(ex instanceof NumberFormatException
                        ? "El efectivo recibido no es válido"
                        : "No se pudo preparar la venta");
            }
        });
    }

    private void finCobro(){cobroEnCurso=false;ventanaPrincipal.getScene().getRoot().setDisable(false);}

    private void autorizarVenta() {
        if(carrito.isEmpty()){mostrarError("Agregue productos o cargue una precuenta.");return;}
        try {
            String body=json.writeValueAsString(VentaRequest.desde(carrito,BigDecimal.ZERO));
            request(ventaCargada==0?"POST":"GET",ventaCargada==0?"/api/ventas":"/api/ventas/"+ventaCargada,body).whenComplete((r,ex) -> Platform.runLater(() -> {
                if(ex!=null || r.statusCode()<200 || r.statusCode()>=300){mostrarError("No se pudo preparar la venta para autorización.");return;}
                try {JsonNode venta=json.readTree(r.body());ventaCargada=venta.path("id").asInt();dialogoAutorizacion(venta);}catch(Exception err){mostrarError("Respuesta de venta inválida.");}
            }));
        }catch(Exception ex){mostrarError("No se pudo preparar la venta.");}
    }
    private void dialogoAutorizacion(JsonNode venta) {
        javafx.scene.control.Dialog<Void> d=new javafx.scene.control.Dialog<>();d.initOwner(ventanaPrincipal);d.setTitle("Autorización administrativa");d.setHeaderText("Venta "+venta.path("folio").asText()+" · $"+venta.path("total").asText());
        d.getDialogPane().getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        javafx.scene.control.ComboBox<String> accion=new javafx.scene.control.ComboBox<>(FXCollections.observableArrayList("DESCUENTO","CAMBIAR_PRECIO","ELIMINAR_PRODUCTO","CANCELAR_VENTA"));accion.setValue("DESCUENTO");
        javafx.scene.control.ComboBox<String> producto=new javafx.scene.control.ComboBox<>();java.util.List<JsonNode> detalles=new java.util.ArrayList<>();venta.path("detalles").forEach(detalles::add);
        detalles.forEach(x -> producto.getItems().add(x.path("producto").path("nombre").asText()));if(!detalles.isEmpty())producto.getSelectionModel().select(0);
        TextField monto=new TextField("0.00"),motivo=new TextField(),usuario=new TextField();javafx.scene.control.PasswordField clave=new javafx.scene.control.PasswordField();
        Label error=new Label();error.setWrapText(true);Button confirmar=new Button("Autorizar operación");confirmar.getStyleClass().add("primary");
        d.getDialogPane().setContent(new VBox(10,new Label("Operación"),accion,new Label("Producto (precio / eliminación)"),producto,new Label("Nuevo precio o descuento ($)"),monto,new Label("Motivo"),motivo,new Label("Usuario administrador"),usuario,new Label("Contraseña"),clave,error,confirmar));
        d.getDialogPane().setPrefWidth(560);d.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CANCEL);
        d.setOnCloseRequest(e -> {if(confirmar.isDisabled())e.consume();});
        d.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL).disableProperty().bind(confirmar.disabledProperty());
        confirmar.setOnAction(e -> {try {
            if(motivo.getText().isBlank()||usuario.getText().isBlank()||clave.getText().isBlank())throw new IllegalArgumentException("Complete motivo y credenciales.");
            var payload=json.createObjectNode().put("accion",accion.getValue()).put("importe",new BigDecimal(monto.getText())).put("motivo",motivo.getText()).put("usuarioAdmin",usuario.getText()).put("claveAdmin",clave.getText());
            int index=producto.getSelectionModel().getSelectedIndex();if(index>=0)payload.put("detalleId",detalles.get(index).path("id").asInt());
            confirmar.setDisable(true);
            request("POST","/api/ventas/"+ventaCargada+"/autorizar",payload.toString()).whenComplete((res,ex) -> Platform.runLater(() -> {
                confirmar.setDisable(false);
                if(ex!=null||res.statusCode()!=200){error.setText(ex!=null ? "No se pudo conectar al servidor." : mensajeRespuesta(res));return;}
                try {JsonNode actual=json.readTree(res.body());carrito.clear();
                    if(actual.path("estado").asText().equals("CANCELADA")){ventaCargada=0;descuentoActual=BigDecimal.ZERO;clienteVenta.setText("Venta cancelada con autorización");}
                    else {descuentoActual=actual.path("descuento").decimalValue();actual.path("detalles").forEach(det -> {JsonNode p=det.path("producto");Renglon fila=new Renglon(p.path("id").asInt(),p.path("nombre").asText(),det.path("precioUnitario").decimalValue());fila.setCantidad(det.path("cantidad").decimalValue());carrito.add(fila);});clienteVenta.setText(actual.path("folio").asText()+" · Descuento autorizado: $"+descuentoActual);}
                    actualizarTotal();clave.clear();d.close();
                }catch(Exception err){error.setText("No se pudo actualizar la venta. Vuelva a cargarla.");}
            }));
        }catch(Exception ex){error.setText("Revise los campos y el importe.");}});d.showAndWait();
    }

    private void cargarPrecuenta() {
        if(!carrito.isEmpty()){mostrarError("Termine la venta actual antes de cargar otra precuenta.");return;}
        request("GET","/api/ventas/pendientes",null).whenComplete((r,ex) -> Platform.runLater(() -> {
            if(ex!=null || r.statusCode()!=200){mostrarError("No se pudieron consultar las precuentas.");return;}
            try {
                javafx.scene.control.ListView<JsonNode> lista=new javafx.scene.control.ListView<>();json.readTree(r.body()).forEach(lista.getItems()::add);
                lista.setPrefSize(650,320);lista.setCellFactory(v -> new javafx.scene.control.ListCell<>(){
                    @Override protected void updateItem(JsonNode n,boolean vacia){super.updateItem(n,vacia);setText(vacia||n==null?null:n.path("folio").asText()+" · "+n.path("dueno").asText("Mostrador")+" · "+n.path("paciente").asText()+" · $"+n.path("total").asText());}
                });
                Alert d=new Alert(Alert.AlertType.CONFIRMATION);prepararDialogo(d);d.setTitle("Precuentas médicas y ventas pendientes");d.setHeaderText("Seleccione la cuenta que desea cobrar");d.getDialogPane().setContent(lista);
                d.showAndWait().filter(b -> b==javafx.scene.control.ButtonType.OK).ifPresent(b -> {
                    JsonNode n=lista.getSelectionModel().getSelectedItem();if(n==null)return;
                    ventaCargada=n.path("id").asInt();descuentoActual=n.path("descuento").decimalValue();
                    n.path("detalles").forEach(det -> {JsonNode p=det.path("producto");Renglon renglon=new Renglon(p.path("id").asInt(),p.path("nombre").asText(),det.path("precioUnitario").decimalValue());renglon.setCantidad(det.path("cantidad").decimalValue());carrito.add(renglon);});
                    clienteVenta.setText(n.path("folio").asText()+" · Dueño: "+n.path("dueno").asText("Mostrador")+" · Paciente: "+n.path("paciente").asText());actualizarTotal();
                });
            }catch(Exception err){mostrarError("Respuesta de precuentas no válida.");}
        }));
    }

    private void mostrarPedidoCompletado(BigDecimal importe, BigDecimal efectivo, String folio) {
        BigDecimal cambio = efectivo.subtract(importe).setScale(2);
        Label titulo = new Label("Pedido completado");
        titulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #21865B;");
        Label detalle = new Label("Total pagado: $" + importe.setScale(2)
                + "\nTotal recibido (todos los medios): $" + formatearDinero(efectivo)
                + "\nCambio para el cliente: $" + formatearDinero(cambio));
        detalle.setStyle("-fx-font-size: 14px; -fx-text-fill: #425466;");
        VBox contenido = new VBox(12, titulo, detalle);
        contenido.setPadding(new Insets(14));
        Alert dialogo = new Alert(Alert.AlertType.INFORMATION);
        dialogo.setTitle("Pedido completado");
        dialogo.setHeaderText(null);
        dialogo.setGraphic(null);
        prepararDialogo(dialogo);
        dialogo.setContentText(null);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().setStyle("-fx-background-color: #F3F6F9;");
        dialogo.showAndWait();
    }

    private void mostrarTicket(List<Renglon> venta, BigDecimal importe,
            BigDecimal efectivo, String folio) {
        VBox ticket = new VBox(7);
        ticket.setPadding(new Insets(18));
        ticket.setStyle("-fx-background-color: white; -fx-border-color: #D7E0E8;");

        Label negocio = new Label(NOMBRE_NEGOCIO);
        negocio.setMaxWidth(Double.MAX_VALUE);
        negocio.setAlignment(javafx.geometry.Pos.CENTER);
        negocio.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #17324D;");
        Label datos = new Label(DIRECCION_SUCURSAL + "\n" + CAJA
                + "\nAtendió: " + atendio
                + "\nFecha: " + LocalDateTime.now().format(FORMATO_FECHA_TICKET)
                + "\nFolio: " + folio + "\nPago: " + ultimoMetodo);
        datos.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px; "
                + "-fx-text-fill: #425466;");
        ticket.getChildren().addAll(negocio, datos, separadorTicket());

        for (Renglon renglon : venta) {
            Label producto = new Label(String.format(Locale.ROOT, "%s x %s%n$%s",
                    renglon.getCantidad().stripTrailingZeros().toPlainString(),
                    renglon.getNombre(), formatearDinero(renglon.getImporte())));
            producto.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px; "
                    + "-fx-text-fill: #425466;");
            ticket.getChildren().add(producto);
        }

        ticket.getChildren().addAll(separadorTicket(),
                lineaTicket("TOTAL", importe),
                lineaTicket("RECIBIDO", efectivo),
                lineaTicket("CAMBIO", efectivo.subtract(importe)));
        Label agradecimiento = new Label("Gracias por su compra");
        agradecimiento.setMaxWidth(Double.MAX_VALUE);
        agradecimiento.setAlignment(javafx.geometry.Pos.CENTER);
        agradecimiento.setStyle("-fx-font-size: 12px; -fx-text-fill: #21865B;");
        ticket.getChildren().add(agradecimiento);

        Alert dialogo = new Alert(Alert.AlertType.INFORMATION);
        dialogo.setTitle("Ticket");
        dialogo.setHeaderText(null);
        dialogo.setGraphic(null);
        prepararDialogo(dialogo);
        dialogo.getDialogPane().setContent(ticket);
        dialogo.getDialogPane().setStyle("-fx-background-color: #F3F6F9;");
        dialogo.getDialogPane().setMinWidth(360);
        dialogo.showAndWait();
    }

    private static Label separadorTicket() {
        Label separador = new Label("--------------------------------");
        separador.setStyle("-fx-font-family: 'Consolas'; -fx-text-fill: #718294;");
        return separador;
    }

    private static Label lineaTicket(String concepto, BigDecimal valor) {
        Label linea = new Label(concepto + ": $" + formatearDinero(valor));
        linea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px; "
                + "-fx-font-weight: bold; -fx-text-fill: #17324D;");
        return linea;
    }

    private void revisarInventario() {
        if (!servidorDisponible) {
            mostrarInventarioTemporal();
            return;
        }
        request("GET", "/api/productos?q=", null).thenAccept(respuesta ->
                Platform.runLater(() -> {
                    if (respuesta.statusCode() != 200) {
                        mostrarError(mensajeRespuesta(respuesta));
                        return;
                    }
                    try {
                        mostrarInventario(json.readTree(respuesta.body()));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                        mostrarError("No se pudo leer el inventario.");
                    }
                }));
    }

    private void mostrarInventarioTemporal() {
        List<JsonNode> productos = PRODUCTOS_DE_PRUEBA.stream()
                .map(this::productoJson)
                .toList();
        mostrarInventario(json.valueToTree(productos));
    }

    private void mostrarInventario(JsonNode productos) {
        TableView<InventarioFila> tablaInventario = new TableView<>();
        TableColumn<InventarioFila, String> nombre = new TableColumn<>("Producto");
        nombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        TableColumn<InventarioFila, String> codigo = new TableColumn<>("Código");
        codigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        TableColumn<InventarioFila, BigDecimal> precio = new TableColumn<>("Precio");
        precio.setCellValueFactory(new PropertyValueFactory<>("precio"));
        precio.setCellFactory(columna -> celdaInventarioMonetaria());
        TableColumn<InventarioFila, BigDecimal> existencia = new TableColumn<>("Existencia");
        existencia.setCellValueFactory(new PropertyValueFactory<>("existencia"));
        existencia.setCellFactory(columna -> celdaInventarioNumero());
        tablaInventario.getColumns().addAll(nombre, codigo, precio, existencia);
        tablaInventario.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tablaInventario.getSelectionModel().setCellSelectionEnabled(true);
        tablaInventario.addEventHandler(KeyEvent.KEY_PRESSED, evento -> {
            if (evento.isShortcutDown() && evento.getCode() == KeyCode.C) {
                copiarCeldaInventario(tablaInventario);
                evento.consume();
            }
        });
        tablaInventario.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaInventario.setPlaceholder(new Label("No hay productos registrados"));
        tablaInventario.setStyle("-fx-background-color: white; -fx-border-color: #D7E0E8;");

        for (JsonNode producto : productos) {
            tablaInventario.getItems().add(new InventarioFila(
                    producto.path("nombre").asText(),
                    producto.path("codigoBarras").isNull() ? "—" : producto.path("codigoBarras").asText(),
                    producto.path("precio").decimalValue(),
                    producto.path("stockActual").decimalValue()));
        }

        Label encabezado = new Label("INVENTARIO DISPONIBLE");
        encabezado.setStyle("-fx-background-color: #17324D; -fx-text-fill: white; "
                + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 12px 14px;");
        VBox contenido = new VBox(12, encabezado, tablaInventario);
        contenido.setPadding(new Insets(16));
        contenido.setStyle("-fx-background-color: #F3F6F9;");
        VBox.setVgrow(tablaInventario, Priority.ALWAYS);

        Alert dialogo = new Alert(Alert.AlertType.INFORMATION);
        dialogo.setTitle("Inventario");
        dialogo.setHeaderText(null);
        dialogo.setGraphic(null);
        prepararDialogo(dialogo);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().setStyle("-fx-background-color: #F3F6F9;");
        dialogo.getDialogPane().setMinWidth(650);
        dialogo.getDialogPane().setMinHeight(450);
        dialogo.showAndWait();
    }

    private void eliminarSeleccionado() {
        if(ventaCargada!=0){mostrarError("La venta guardada requiere autorización para modificar sus productos.");return;}
        Renglon seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado != null) {
            carrito.remove(seleccionado);
            actualizarTotal();
        }
    }

    private BigDecimal total() {
        return carrito.stream().map(Renglon::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add).subtract(descuentoActual).setScale(2,java.math.RoundingMode.HALF_UP);
    }

    private void actualizarTotal() {
        total.setText("$" + formatearDinero(total()));
        BigDecimal unidades = carrito.stream().map(Renglon::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        resumenCarrito.setText(unidades.stripTrailingZeros().toPlainString()
                + (unidades.compareTo(BigDecimal.ONE) == 0 ? " producto" : " productos"));
    }

    private static String formatearDinero(BigDecimal valor) {
        return valor.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static TableCell<Renglon, BigDecimal> celdaMonetaria() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean vacia) {
                super.updateItem(valor, vacia);
                setText(vacia || valor == null ? null : formatearDinero(valor));
            }
        };
    }

    private static TableCell<InventarioFila, BigDecimal> celdaInventarioMonetaria() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean vacia) {
                super.updateItem(valor, vacia);
                setText(vacia || valor == null ? null : formatearDinero(valor));
            }
        };
    }

    private static TableCell<InventarioFila, BigDecimal> celdaInventarioNumero() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean vacia) {
                super.updateItem(valor, vacia);
                setText(vacia || valor == null ? null : valor.stripTrailingZeros().toPlainString());
            }
        };
    }

    private static void copiarCeldaInventario(TableView<InventarioFila> tablaInventario) {
        if (tablaInventario.getSelectionModel().getSelectedCells().isEmpty()) {
            return;
        }
        javafx.scene.control.TablePosition<?, ?> posicion =
                tablaInventario.getSelectionModel().getSelectedCells().get(0);
        InventarioFila fila = tablaInventario.getItems().get(posicion.getRow());
        String valor = switch (posicion.getColumn()) {
            case 0 -> fila.getNombre();
            case 1 -> fila.getCodigo();
            case 2 -> formatearDinero(fila.getPrecio());
            case 3 -> fila.getExistencia().stripTrailingZeros().toPlainString();
            default -> "";
        };
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(valor);
        Clipboard.getSystemClipboard().setContent(contenido);
    }

    private void verificarServidor() {
        request("GET", "/api/salud", null).thenAccept(r -> Platform.runLater(() ->
                {
                    servidorDisponible = r.statusCode() == 200;
                    estadoServidor.setText(servidorDisponible
                            ? "Servidor: conectado"
                            : "Modo local · datos de prueba");
                    estadoServidor.getStyleClass().removeAll("connected", "disconnected");
                    estadoServidor.getStyleClass().add(servidorDisponible ? "connected" : "disconnected");
                })).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        servidorDisponible = false;
                        estadoServidor.setText("Modo local · datos de prueba");
                        estadoServidor.getStyleClass().removeAll("connected", "disconnected");
                        estadoServidor.getStyleClass().add("disconnected");
                    });
                    return null;
                });
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> request(
            String metodo, String ruta, String cuerpo) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(SERVIDOR + ruta))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if ("POST".equals(metodo)) builder.POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        else builder.GET();
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String texto) {
        return java.net.URLEncoder.encode(texto, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void mostrarError(String mensaje) {
        mostrarError("No se pudo completar la operación",
                mensaje == null ? "Error de comunicación" : mensaje);
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert dialogo = new Alert(Alert.AlertType.ERROR, mensaje);
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(null);
        dialogo.setGraphic(null);
        prepararDialogo(dialogo);
        dialogo.showAndWait();
    }

    private void mostrarOperacionFallida(HttpResponse<String> respuesta) {
        String titulo = "No se pudo completar la operación";
        mostrarError(titulo, mensajeRespuesta(respuesta));
    }

    private String mensajeRespuesta(HttpResponse<String> respuesta) {
        try {
            JsonNode cuerpo = json.readTree(respuesta.body());
            for (String campo : List.of("message", "detail", "error")) {
                JsonNode valor = cuerpo.get(campo);
                if (valor != null && !valor.asText().isBlank()
                        && !"Conflict".equalsIgnoreCase(valor.asText())) {
                    return valor.asText();
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            // Se usa el estado HTTP cuando el servidor no devuelve JSON válido.
        }
        if (respuesta.statusCode() == 409) {
            return "No se puede registrar la venta porque la existencia disponible "
                    + "no cubre la cantidad solicitada. Revise las cantidades del carrito.";
        }
        return "No se pudo completar la operación (HTTP " + respuesta.statusCode() + ").";
    }

    private void mostrarInfo(String mensaje) {
        Alert dialogo = new Alert(Alert.AlertType.INFORMATION, mensaje);
        dialogo.setGraphic(null);
        prepararDialogo(dialogo);
        dialogo.showAndWait();
    }

    private void prepararDialogo(Alert dialogo) {
        dialogo.getDialogPane().getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        dialogo.initOwner(ventanaPrincipal);
        dialogo.setOnShown(evento -> {
            Window ventanaDialogo = dialogo.getDialogPane().getScene().getWindow();
            ventanaDialogo.setX(ventanaPrincipal.getX()
                    + (ventanaPrincipal.getWidth() - ventanaDialogo.getWidth()) / 2);
            ventanaDialogo.setY(ventanaPrincipal.getY()
                    + (ventanaPrincipal.getHeight() - ventanaDialogo.getHeight()) / 2);
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

    public static class Renglon {
        private final int id;
        private final String nombre;
        private final BigDecimal precio;
        private BigDecimal cantidad = BigDecimal.ONE;

        Renglon(int id, String nombre, BigDecimal precio) {
            this.id = id;
            this.nombre = nombre;
            this.precio = precio;
        }
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        public String getNombre() { return nombre; }
        public BigDecimal getPrecio() { return precio; }
        public BigDecimal getImporte() { return precio.multiply(cantidad); }
    }

    private record VentaRequest(List<RenglonRequest> detalles, BigDecimal descuento) {
        static VentaRequest desde(List<Renglon> renglones, BigDecimal descuento) {
            return new VentaRequest(
                    renglones.stream().map(r -> new RenglonRequest(r.id, r.cantidad)).toList(),
                    descuento);
        }
    }

    private record RenglonRequest(int productoId, BigDecimal cantidad) {}

    private record ProductoTemporal(int id, String codigo, String nombre, BigDecimal precio, BigDecimal stock) {}

    public static class InventarioFila {
        private final String nombre;
        private final String codigo;
        private final BigDecimal precio;
        private final BigDecimal existencia;

        InventarioFila(String nombre, String codigo, BigDecimal precio, BigDecimal existencia) {
            this.nombre = nombre;
            this.codigo = codigo;
            this.precio = precio;
            this.existencia = existencia;
        }

        public String getNombre() { return nombre; }
        public String getCodigo() { return codigo; }
        public BigDecimal getPrecio() { return precio; }
        public BigDecimal getExistencia() { return existencia; }
    }

    //hola

    //dddddddd
}
