package mx.buap.veterinaria.consultorio;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Protección CSRF: el servidor entrega la cookie XSRF-TOKEN y exige devolverla en la cabecera
 * X-XSRF-TOKEN en cada POST, PUT, PATCH o DELETE. Si todavía no hay cookie (por ejemplo, una ventana
 * abierta desde el portal con la sesión heredada) se pide una vez a /api/auth/sesion.
 */
final class Csrf {
    private Csrf() {}

    static HttpRequest.Builder aplicar(HttpClient http, String servidor, String metodo, HttpRequest.Builder builder) {
        if (metodo == null || "GET".equalsIgnoreCase(metodo)) return builder;
        Optional<String> token = token(http);
        if (token.isEmpty()) {
            try {
                http.send(HttpRequest.newBuilder(URI.create(servidor + "/api/auth/sesion")).timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignorada) { /* sin conexión: la petición fallará y se mostrará el error habitual */ }
            if (Thread.currentThread().isInterrupted()) Thread.interrupted();
            token = token(http);
        }
        token.ifPresent(t -> builder.header("X-XSRF-TOKEN", t));
        return builder;
    }

    static Optional<String> token(HttpClient http) {
        return http.cookieHandler().filter(CookieManager.class::isInstance).map(CookieManager.class::cast)
                .flatMap(c -> c.getCookieStore().getCookies().stream()
                        .filter(k -> "XSRF-TOKEN".equals(k.getName())).map(HttpCookie::getValue).findFirst());
    }
}
