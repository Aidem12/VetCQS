package mx.buap.veterinaria.portal;

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

/** Consulta administrativa de cortes cerrados, con impresión y exportación. */
final class CortesView {
    private final HttpClient http;
    private final Stage owner;
    private final ObjectMapper json = new ObjectMapper();
    private final String server;
    CortesView(HttpClient http, Stage owner, String server) { this.http=http; this.owner=owner; this.server=server; }
    private Dialog<Void> ventana(String titulo) {
        Dialog<Void> d=new Dialog<>();d.initOwner(owner);d.setTitle(titulo);d.setHeaderText(titulo);d.setResizable(true);
        d.getDialogPane().getStylesheets().add(getClass().getResource("/mx/buap/veterinaria/ui/veterinaria.css").toExternalForm());
        d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);return d;
    }
    void historial() {
        Dialog<Void> d=ventana("Histórico de cortes");ListView<JsonNode> lista=new ListView<>();lista.setPrefSize(550,330);
        lista.setCellFactory(v -> new ListCell<>(){@Override protected void updateItem(JsonNode n,boolean vacia){super.updateItem(n,vacia);setText(vacia||n==null?null:"Corte #"+n.path("id").asText()+" · "+n.path("cajero").asText()+" · "+n.path("cierre").asText());}});
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
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(server+ruta)).timeout(Duration.ofSeconds(12)).header("Content-Type","application/json");
        HttpRequest req=metodo.equals("GET")?b.GET().build():b.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        http.sendAsync(req,HttpResponse.BodyHandlers.ofString()).whenComplete((response,ex) -> Platform.runLater(() -> {
            boton.setDisable(false);dialogo.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);if(!dialogo.isShowing())return;
            if(ex!=null){error.setText("No se pudo conectar al servidor.");return;}
            if(response.statusCode()<200 || response.statusCode()>=300){try {JsonNode err=json.readTree(response.body());error.setText(err.path("message").asText(err.path("error").asText("Operación rechazada ("+response.statusCode()+").")));}catch(Exception ignored){error.setText("Operación rechazada ("+response.statusCode()+").");}return;}
            try{success.accept(json.readTree(response.body()));}catch(Exception err){error.setText("No se pudo leer la respuesta.");}
        }));
    }
}
