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

/**
 * Canal de avisos a caja (RNF-10). Cuando el consultorio envía una precuenta,
 * el servidor llama a {@link AvisosCaja#avisar(String)} y cada caja conectada
 * recibe el mensaje al instante, sin tener que preguntar cada cierto tiempo.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AvisosCaja avisos;

    public WebSocketConfig(AvisosCaja avisos) {
        this.avisos = avisos;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(avisos, "/ws/caja").setAllowedOriginPatterns("*");
    }

    @Component
    public static class AvisosCaja extends TextWebSocketHandler {
        private final Set<WebSocketSession> cajas = new CopyOnWriteArraySet<>();

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            cajas.add(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            cajas.remove(session);
        }

        public void avisar(String json) {
            for (WebSocketSession caja : cajas) {
                try {
                    caja.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    cajas.remove(caja);
                }
            }
        }
    }
}
