package mx.buap.veterinaria.caja;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Suscripción en tiempo real a los avisos de Caja (/ws/caja).
 * - Reconecta sola con espera creciente (2 s hasta 30 s) si el servidor se reinicia o la red falla.
 * - Envía un latido cada 25 s para detectar conexiones muertas.
 * - Al (re)conectar recupera por la API los avisos que se emitieron mientras estaba desconectada,
 *   así ningún aviso se pierde ni se muestra dos veces (se recuerda el último id visto).
 * Las retrollamadas se ejecutan fuera del hilo de JavaFX: quien las use debe pasar a Platform.runLater.
 */
final class CanalAvisos {
    private final HttpClient http;
    private final String servidor;
    private final ObjectMapper json = new ObjectMapper();
    private final Consumer<JsonNode> alRecibir;
    private final Consumer<Boolean> alCambiarEstado;
    private final ScheduledExecutorService tareas = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "avisos-caja"); t.setDaemon(true); return t;
    });
    private volatile WebSocket socket;
    private volatile boolean detenido;
    private volatile int ultimoId = -1;
    private int espera = 2;

    CanalAvisos(HttpClient http, String servidor, Consumer<JsonNode> alRecibir, Consumer<Boolean> alCambiarEstado) {
        this.http = http; this.servidor = servidor; this.alRecibir = alRecibir; this.alCambiarEstado = alCambiarEstado;
    }

    void iniciar() {
        tareas.scheduleAtFixedRate(() -> {
            WebSocket s = socket;
            if (s != null && !s.isOutputClosed()) s.sendText("ping", true);
        }, 25, 25, TimeUnit.SECONDS);
        tareas.execute(this::conectar);
    }

    void detener() {
        detenido = true;
        WebSocket s = socket;
        if (s != null) s.abort();
        tareas.shutdownNow();
    }

    private void conectar() {
        if (detenido) return;
        String ws = servidor.replaceFirst("^http", "ws") + "/ws/caja";
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create(ws), new WebSocket.Listener() {
            private final StringBuilder parcial = new StringBuilder();
            @Override public void onOpen(WebSocket w) {
                socket = w; espera = 2; alCambiarEstado.accept(true); recuperar(); w.request(1);
            }
            @Override public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                parcial.append(data);
                if (last) { procesar(parcial.toString()); parcial.setLength(0); }
                w.request(1); return null;
            }
            @Override public CompletionStage<?> onClose(WebSocket w, int codigo, String razon) { perdida(); return null; }
            @Override public void onError(WebSocket w, Throwable error) { perdida(); }
        }).exceptionally(error -> { perdida(); return null; });
    }

    private void perdida() {
        socket = null;
        alCambiarEstado.accept(false);
        if (detenido) return;
        int segundos = espera;
        espera = Math.min(30, espera * 2);
        try { tareas.schedule(this::conectar, segundos, TimeUnit.SECONDS); } catch (java.util.concurrent.RejectedExecutionException ignorada) { }
    }

    private void procesar(String texto) {
        try {
            JsonNode nodo = json.readTree(texto);
            if ("AVISO".equals(nodo.path("tipo").asText())) entregar(nodo.path("aviso"));
        } catch (Exception ignorado) { /* mensaje desconocido */ }
    }

    /** Pide a la API los avisos posteriores al último visto (la primera vez solo marca el punto de partida). */
    private void recuperar() {
        int desde = Math.max(ultimoId, 0);
        HttpRequest r = HttpRequest.newBuilder(URI.create(servidor + "/api/ventas/avisos?desde=" + desde)).timeout(Duration.ofSeconds(8)).GET().build();
        http.sendAsync(r, HttpResponse.BodyHandlers.ofString()).thenAccept(resp -> {
            if (resp.statusCode() != 200) return;
            try {
                JsonNode lista = json.readTree(resp.body());
                if (ultimoId < 0) {   // arranque: no se repiten avisos viejos, solo se recuerda el último
                    ultimoId = lista.isEmpty() ? 0 : lista.get(lista.size() - 1).path("id").asInt();
                    return;
                }
                lista.forEach(this::entregar);
            } catch (Exception ignorado) { }
        });
    }

    private synchronized void entregar(JsonNode aviso) {
        int id = aviso.path("id").asInt();
        if (id <= ultimoId && ultimoId >= 0) return;
        ultimoId = Math.max(ultimoId, id);
        alRecibir.accept(aviso);
    }
}
