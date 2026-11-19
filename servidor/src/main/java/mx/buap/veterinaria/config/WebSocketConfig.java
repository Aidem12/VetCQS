package mx.buap.veterinaria.config;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;

/**
 * Canal de avisos a caja (RNF-10). Cuando el consultorio envía una precuenta,
 * el servidor llama a {@link AvisosCaja#avisar(String)} y cada caja conectada
 * recibe el mensaje al instante, sin tener que preguntar cada cierto tiempo.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AvisosCaja avisos;
    @Value("${app.allowed-origins:http://localhost:8080}")
    private String allowedOrigin;

    public WebSocketConfig(AvisosCaja avisos) {
        this.avisos = avisos;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(avisos, "/ws/caja")
                .addInterceptors(new org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor())
                .setAllowedOriginPatterns(allowedOrigin.split("\\s*,\\s*"));
    }

    @Component
    public static class AvisosCaja extends TextWebSocketHandler {
        private final Set<WebSocketSession> cajas = new CopyOnWriteArraySet<>();

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            cajas.add(session);
        }

        /** Mensajes del cliente: solo se responde al latido para que Caja detecte conexiones muertas. */
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
            if ("ping".equals(message.getPayload())) session.sendMessage(new TextMessage("{\"tipo\":\"PONG\"}"));
        }

        /** Cierra los WebSocket abiertos con una sesión HTTP que ya terminó (logout, expiración o desactivación). */
        public void cerrarSesion(String httpSessionId) {
            for (WebSocketSession caja : cajas) {
                Object id = caja.getAttributes().get(
                        org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
                if (httpSessionId != null && httpSessionId.equals(id)) {
                    cajas.remove(caja);
                    try { caja.close(CloseStatus.POLICY_VIOLATION.withReason("Sesión cerrada")); } catch (IOException ignored) { }
                }
            }
        }

        public int conectadas() { return cajas.size(); }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            cajas.remove(session);
        }

        public void avisar(String json) {
            for (WebSocketSession caja : cajas) {
                try {
                    synchronized (caja) { caja.sendMessage(new TextMessage(json)); }
                } catch (IOException | IllegalStateException e) {
                    cajas.remove(caja);
                }
            }
        }
    }
}
