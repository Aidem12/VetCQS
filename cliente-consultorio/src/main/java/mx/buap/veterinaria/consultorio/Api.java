package mx.buap.veterinaria.consultorio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;

/** Llamadas a la API para las ventanas de Agenda y Expediente: JSON, descargas binarias y subida de archivos. */
final class Api {
    final HttpClient http;
    final String servidor;
    final ObjectMapper json = new ObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    Api(HttpClient http, String servidor) { this.http = http; this.servidor = servidor; }

    ObjectNode objeto() { return json.createObjectNode(); }

    void get(String ruta, Consumer<JsonNode> ok, Consumer<String> falla) { json("GET", ruta, null, ok, falla); }
    void post(String ruta, JsonNode cuerpo, Consumer<JsonNode> ok, Consumer<String> falla) { json("POST", ruta, cuerpo, ok, falla); }
    void put(String ruta, JsonNode cuerpo, Consumer<JsonNode> ok, Consumer<String> falla) { json("PUT", ruta, cuerpo, ok, falla); }
    void delete(String ruta, Consumer<JsonNode> ok, Consumer<String> falla) { json("DELETE", ruta, null, ok, falla); }

    void json(String metodo, String ruta, JsonNode cuerpo, Consumer<JsonNode> ok, Consumer<String> falla) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(servidor + ruta)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");
        Csrf.aplicar(http, servidor, metodo, b);
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo.toString()));
        enviar(b.build(), ok, falla);
    }

    /** Descarga un archivo (PDF, Excel o adjunto) y lo guarda en {@code destino}. */
    void descargar(String ruta, Path destino, Runnable ok, Consumer<String> falla) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(servidor + ruta)).timeout(Duration.ofSeconds(30)).GET().build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((r, ex) -> Platform.runLater(() -> {
            if (ex != null) { falla.accept("No se pudo conectar al servidor."); return; }
            if (r.statusCode() != 200) { falla.accept(mensaje(r.statusCode(), new String(r.body(), StandardCharsets.UTF_8))); return; }
            try { Files.write(destino, r.body()); ok.run(); } catch (Exception e) { falla.accept("No se pudo guardar el archivo: " + e.getMessage()); }
        }));
    }

    /** Sube un archivo como multipart/form-data en el campo "archivo". */
    void subir(String ruta, Path archivo, Consumer<JsonNode> ok, Consumer<String> falla) {
        try {
            String limite = "----vetcqs" + UUID.randomUUID();
            String nombre = archivo.getFileName().toString().replace("\"", "");
            String tipo = Files.probeContentType(archivo);
            byte[] inicio = ("--" + limite + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"" + nombre
                    + "\"\r\nContent-Type: " + (tipo == null ? "application/octet-stream" : tipo) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] fin = ("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] datos = Files.readAllBytes(archivo);
            byte[] cuerpo = new byte[inicio.length + datos.length + fin.length];
            System.arraycopy(inicio, 0, cuerpo, 0, inicio.length);
            System.arraycopy(datos, 0, cuerpo, inicio.length, datos.length);
            System.arraycopy(fin, 0, cuerpo, inicio.length + datos.length, fin.length);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(servidor + ruta)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "multipart/form-data; boundary=" + limite);
            Csrf.aplicar(http, servidor, "POST", b);
            enviar(b.POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo)).build(), ok, falla);
        } catch (Exception e) { falla.accept("No se pudo leer el archivo: " + e.getMessage()); }
    }

    private void enviar(HttpRequest req, Consumer<JsonNode> ok, Consumer<String> falla) {
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((r, ex) -> Platform.runLater(() -> {
            if (ex != null) { falla.accept("No se pudo conectar al servidor."); return; }
            if (r.statusCode() < 200 || r.statusCode() >= 300) { falla.accept(mensaje(r.statusCode(), r.body())); return; }
            try { ok.accept(r.body() == null || r.body().isBlank() ? json.nullNode() : json.readTree(r.body())); }
            catch (Exception e) { falla.accept("La respuesta del servidor no es válida."); }
        }));
    }

    String mensaje(int codigo, String cuerpo) {
        try {
            JsonNode n = json.readTree(cuerpo);
            for (String k : new String[]{"message", "error", "detail"})
                if (n.hasNonNull(k) && !n.path(k).asText().isBlank()) return n.path(k).asText();
        } catch (Exception ignorada) { }
        return switch (codigo) {
            case 401 -> "Su sesión terminó. Cierre esta ventana e inicie sesión de nuevo.";
            case 403 -> "Su usuario no tiene permiso para esta operación.";
            case 404 -> "No se encontró la información solicitada.";
            case 409 -> "La operación entra en conflicto con información existente.";
            default -> "Operación rechazada (" + codigo + ").";
        };
    }
}
