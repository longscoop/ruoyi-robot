package com.robot.platform.ai.model.realtime.qwen;

import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.RealtimeVoiceClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
public class QwenRealtimeVoiceClient implements RealtimeVoiceClient {

    private final QwenRealtimeCodec codec;
    private final WebSocketConnector connector;

    public QwenRealtimeVoiceClient(QwenRealtimeCodec codec) {
        this(codec, new JdkWebSocketConnector(HttpClient.newHttpClient()));
    }

    QwenRealtimeVoiceClient(QwenRealtimeCodec codec, WebSocketConnector connector) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.connector = Objects.requireNonNull(connector, "connector");
    }

    @Override
    public String providerType() {
        return "QWEN";
    }

    @Override
    public RealtimeProviderSession open(ResolvedModel model, RealtimeProviderListener listener) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(listener, "listener");
        if (!"QWEN".equals(model.providerType())) {
            throw new IllegalArgumentException("Qwen realtime client requires providerType=QWEN");
        }
        if (!"REALTIME_S2S".equals(model.modelType())) {
            throw new IllegalArgumentException("Qwen realtime client requires modelType=REALTIME_S2S");
        }
        if (model.credential() == null || model.credential().isBlank()) {
            throw new IllegalArgumentException("Qwen provider credential must not be blank");
        }

        URI uri = buildUri(model.baseUrl(), model.modelCode());
        QwenRealtimeSession session = new QwenRealtimeSession(codec, model, listener);
        WebSocket webSocket = connector.connect(uri, "Bearer " + model.credential(), session).join();
        session.bind(webSocket);
        return session;
    }

    private static URI buildUri(String baseUrl, String modelCode) {
        String encodedModel = URLEncoder.encode(modelCode, StandardCharsets.UTF_8).replace("+", "%20");
        String separator = baseUrl.contains("?") ? "&" : "?";
        return URI.create(baseUrl + separator + "model=" + encodedModel);
    }

    interface WebSocketConnector {
        CompletableFuture<WebSocket> connect(URI uri, String authorization, WebSocket.Listener listener);
    }

    private record JdkWebSocketConnector(HttpClient httpClient) implements WebSocketConnector {
        @Override
        public CompletableFuture<WebSocket> connect(URI uri, String authorization, WebSocket.Listener listener) {
            return httpClient.newWebSocketBuilder()
                    .header("Authorization", authorization)
                    .buildAsync(uri, listener);
        }
    }
}
