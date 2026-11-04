package mx.buap.veterinaria.caja;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Operaciones de turno: el esperado solo se solicita después del conteo confirmado. */
final class TurnosView {
    private final HttpClient http;
    private final Stage owner;
    private final ObjectMapper json = new ObjectMapper();
    TurnosView(HttpClient http, Stage owner) { this.http=http; this.owner=owner; }
    HBox acciones() {
        Button abrir=new Button("Abrir turno"), salida=new Button("Salida de efectivo"), corte=new Button("Corte ciego"), historial=new Button("Histórico de cortes");
        abrir.setOnAction(e -> abrir()); salida.setOnAction(e -> salida()); corte.setOnAction(e -> corte()); historial.setOnAction(e -> historial());
        HBox bar=new HBox(8,abrir,salida,corte,historial);bar.setPadding(new Insets(10,18,0,18));return bar;
    }
    private Dialog<Void> ventana(String titulo) {
        Dialog<Void> d=new Dialog<>();d.initOwner(owner);d.setTitle(titulo);d.setHeaderText(titulo);d.setResizable(true);
        d.getDialogPane().getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);return d;
    }
    private void abrir() {
        Dialog<Void> d=ventana("Apertura de turno");TextField fondo=new TextField("0.00");Label error=new Label();Button guardar=new Button("Abrir turno");
        guardar.getStyleClass().add("primary");d.getDialogPane().setContent(new VBox(12,new Label("Fondo inicial de efectivo ($)"),fondo,error,guardar));
        guardar.setOnAction(e -> {try {BigDecimal n=numero(fondo);enviar(d,guardar,error,"POST","/api/turnos",json.createObjectNode().put("fondoInicial",n),r -> d.close());}catch(Exception ex){error.setText(ex.getMessage());}});d.showAndWait();
    }
    private void salida() {
        Dialog<Void> d=ventana("Salida de efectivo · Autorización administrativa");
        TextField concepto=new TextField(),monto=new TextField(),usuario=new TextField();PasswordField clave=new PasswordField();Label error=new Label();error.setWrapText(true);
        Button guardar=new Button("Autorizar y registrar salida");guardar.getStyleClass().add("primary");
        d.getDialogPane().setPrefWidth(540);d.getDialogPane().setContent(new VBox(10,new Label("Concepto"),concepto,new Label("Importe ($)"),monto,new Separator(),new Label("Usuario administrador"),usuario,new Label("Contraseña"),clave,error,guardar));
        guardar.setOnAction(e -> {try {
            if(concepto.getText().isBlank() || usuario.getText().isBlank() || clave.getText().isBlank())throw new IllegalArgumentException("Complete concepto y credenciales.");
            BigDecimal n=numero(monto);if(n.signum()==0)throw new IllegalArgumentException("El importe debe ser mayor a cero.");
            ObjectNode body=json.createObjectNode().put("concepto",concepto.getText().trim()).put("monto",n).put("usuarioAdmin",usuario.getText().trim()).put("claveAdmin",clave.getText());
            enviar(d,guardar,error,"POST","/api/turnos/salidas",body,r -> {clave.clear();d.close();});
        }catch(Exception ex){error.setText(ex.getMessage());}});d.showAndWait();
    }
    private void corte() {
        Dialog<Void> d=ventana("Corte ciego · Conteo físico");GridPane grid=new GridPane();grid.setHgap(18);grid.setVgap(8);
        Map<String,TextField> campos=new LinkedHashMap<>();Label total=new Label("Efectivo contado: $0.00"),error=new Label();error.setWrapText(true);
        int fila=0;
        for(String denominacion:List.of("1000","500","200","100","50","20","10","5","2","1","0.50")) {
            TextField piezas=new TextField("0");piezas.setPrefColumnCount(5);campos.put(denominacion,piezas);
            grid.addRow(fila++,new Label("$"+denominacion),piezas);
            piezas.textProperty().addListener((o,a,b) -> {try{total.setText("Efectivo contado: $"+contado(campos));}catch(Exception ex){total.setText("Revise las cantidades de piezas");}});
        }
        TextField tarjeta=new TextField("0.00"),transferencia=new TextField("0.00");
        VBox declaraciones=new VBox(12,new Label("Declaración de comprobantes"),new Label("Tarjetas ($)"),tarjeta,new Label("Transferencias ($)"),transferencia,new Separator(),total,
                new Label("El saldo esperado se revela al confirmar el cierre."));
        Button cerrar=new Button("Confirmar conteo y cerrar turno");cerrar.getStyleClass().add("primary");
        VBox content=new VBox(16,new HBox(30,grid,declaraciones),error,cerrar);content.setPadding(new Insets(10));d.getDialogPane().setContent(content);
        cerrar.setOnAction(e -> {try {
            ObjectNode body=json.createObjectNode().put("efectivoContado",contado(campos)).put("tarjetaContada",numero(tarjeta)).put("transferenciaContada",numero(transferencia));
            ObjectNode desglose=body.putObject("denominaciones");campos.forEach((k,v) -> desglose.put(k,Integer.parseInt(v.getText().trim())));
            enviar(d,cerrar,error,"POST","/api/turnos/cierre",body,r -> {d.close();reporte(r);});
        }catch(Exception ex){error.setText("Revise el conteo: piezas enteras no negativas y montos con máximo dos decimales.");}});d.showAndWait();
    }
    private BigDecimal contado(Map<String,TextField> campos) {
        BigDecimal total=BigDecimal.ZERO;
        for(var e:campos.entrySet()){int n=Integer.parseInt(e.getValue().getText().trim());if(n<0 || n>1000000)throw new IllegalArgumentException();total=total.add(new BigDecimal(e.getKey()).multiply(BigDecimal.valueOf(n)));}return total;
    }
    private BigDecimal numero(TextField campo) {
        BigDecimal n=new BigDecimal(campo.getText().trim().replace(',','.'));
        if(n.signum()<0 || n.scale()>2 || n.compareTo(new BigDecimal("99999999.99"))>0)throw new IllegalArgumentException("Use un importe positivo con máximo dos decimales.");return n;
    }
    private void historial() {
        Dialog<Void> d=ventana("Histórico de cortes");ListView<JsonNode> lista=new ListView<>();lista.setPrefSize(550,330);
        lista.setCellFactory(v -> new ListCell<>(){@Override protected void updateItem(JsonNode n,boolean vacia){super.updateItem(n,vacia);setText(vacia||n==null?null:"Corte #"+n.path("id").asText()+" · "+n.path("cierre").asText());}});
        Button ver=new Button("Ver reporte");Label error=new Label();d.getDialogPane().setContent(new VBox(12,lista,error,ver));
        ver.setOnAction(e -> {JsonNode n=lista.getSelectionModel().getSelectedItem();if(n!=null)enviar(d,ver,error,"GET","/api/turnos/"+n.path("id").asInt()+"/reporte",null,this::reporte);});
        d.setOnShown(e -> enviar(d,ver,error,"GET","/api/turnos/historial",null,r -> {lista.getItems().clear();r.forEach(lista.getItems()::add);}));d.showAndWait();
    }
    private void reporte(JsonNode r) {
        Dialog<Void> d=ventana("Balance y auditoría · Corte #"+r.path("id").asText());
        StringBuilder s=new StringBuilder("VetCQS · REPORTE DE CORTE\n\n");
        String[][] filas={{"cajero","Cajero"},{"apertura","Apertura"},{"cierre","Cierre"},{"fondoInicial","Fondo inicial"},{"ventasEfectivo","Ventas en efectivo"},{"ventasTarjeta","Ventas con tarjeta"},{"ventasTransferencia","Transferencias"},{"salidas","Salidas autorizadas"},{"efectivoEsperado","Efectivo esperado"},{"efectivoContado","Efectivo contado"},{"diferencia","Diferencia de efectivo"},{"tarjetaContada","Tarjetas declaradas"},{"transferenciaContada","Transferencias declaradas"}};
        for(String[] f:filas)s.append(f[1]).append(": ").append(r.path(f[0]).asText("—")).append('\n');
        s.append("\nCONTEO POR DENOMINACIÓN\n");r.path("denominaciones").fields().forEachRemaining(e -> s.append("$").append(e.getKey()).append(" × ").append(e.getValue().asText()).append('\n'));
        s.append("\nTRANSACCIONES\n");r.path("transacciones").forEach(v -> s.append(v.path("folio").asText()).append(" · ").append(v.path("estado").asText()).append(" · $").append(v.path("total").asText()).append('\n'));
        TextArea texto=new TextArea(s.toString());texto.setEditable(false);texto.setPrefSize(680,500);
        Label dictamen=new Label(r.path("diferencia").decimalValue().signum()==0?"Efectivo cuadrado":"Diferencia de efectivo registrada");dictamen.getStyleClass().add("page-title");
        Button exportar=new Button("Guardar reporte TXT"),imprimir=new Button("Imprimir / PDF");Label estado=new Label();
        exportar.setOnAction(e -> {javafx.stage.FileChooser chooser=new javafx.stage.FileChooser();chooser.setInitialFileName("corte-"+r.path("id").asText()+".txt");java.io.File f=chooser.showSaveDialog(owner);if(f!=null)try{java.nio.file.Files.writeString(f.toPath(),s.toString());estado.setText("Reporte guardado.");}catch(Exception ex){estado.setText("No se pudo guardar el reporte.");}});
        imprimir.setOnAction(e -> {javafx.print.PrinterJob job=javafx.print.PrinterJob.createPrinterJob();if(job==null){estado.setText("No hay una impresora configurada.");return;}if(job.showPrintDialog(owner)){String[] lineas=s.toString().split("\\n");boolean ok=true;for(int i=0;i<lineas.length;i+=40){Label papel=new Label(String.join("\n",java.util.Arrays.copyOfRange(lineas,i,Math.min(i+40,lineas.length))));papel.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");if(!job.printPage(papel)){ok=false;break;}}if(ok)job.endJob();else job.cancelJob();}});
        d.getDialogPane().setContent(new VBox(12,dictamen,texto,new HBox(8,exportar,imprimir),estado));d.showAndWait();
    }
    private void enviar(Dialog<?> dialogo,Button boton,Label error,String metodo,String ruta,JsonNode body,Consumer<JsonNode> success) {
        boton.setDisable(true);error.setText("");
        dialogo.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
        dialogo.setOnCloseRequest(e -> {if(boton.isDisabled())e.consume();});
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(CajaApp.SERVIDOR+ruta)).timeout(Duration.ofSeconds(12)).header("Content-Type","application/json");
        HttpRequest req=metodo.equals("GET")?b.GET().build():b.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        http.sendAsync(req,HttpResponse.BodyHandlers.ofString()).whenComplete((response,ex) -> Platform.runLater(() -> {
            boton.setDisable(false);dialogo.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);if(!dialogo.isShowing())return;
            if(ex!=null){error.setText("No se pudo conectar al servidor.");return;}
            if(response.statusCode()<200 || response.statusCode()>=300){error.setText("Operación rechazada ("+response.statusCode()+"). Revise turno, importes y credenciales.");return;}
            try{success.accept(json.readTree(response.body()));}catch(Exception err){error.setText("No se pudo leer la respuesta.");}
        }));
    }
}
