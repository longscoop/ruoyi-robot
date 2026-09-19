package com.robot.platform.ai.model.realtime.doubao;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.RealtimeVoiceClient;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DoubaoRealtimeVoiceClient implements RealtimeVoiceClient {

    private final DoubaoRealtimeCodec codec;
    private final WebSocketConnector connector;

    public DoubaoRealtimeVoiceClient(DoubaoRealtimeCodec codec) {
        this(codec, new JdkWebSocketConnector(HttpClient.newHttpClient()));
    }

    DoubaoRealtimeVoiceClient(DoubaoRealtimeCodec codec, WebSocketConnector connector) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.connector = Objects.requireNonNull(connector, "connector");
    }

    @Override
    public String providerType() {
        return "DOUBAO";
    }

    @Override
    public RealtimeProviderSession open(ResolvedModel model, RealtimeProviderListener listener) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(listener, "listener");
        if (!"DOUBAO".equals(model.providerType())) {
            throw new IllegalArgumentException("Doubao realtime client requires providerType=DOUBAO");
        }
        if (!"REALTIME_S2S".equals(model.modelType())) {
            throw new IllegalArgumentException("Doubao realtime client requires modelType=REALTIME_S2S");
        }
        if (model.credential() == null || model.credential().isBlank()) {
            throw new IllegalArgumentException("Doubao provider credential must not be blank");
        }

        ProviderConfig provider = ProviderConfig.parse(model.providerConfigJson());
        ModelConfig config = ModelConfig.parse(model.modelConfigJson(), model.realtimeInstructions());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Api-Resource-Id", provider.resourceId());
        headers.put("X-Api-Connect-Id", UUID.randomUUID().toString());
        if ("API_KEY".equals(provider.authType())) {
            headers.put("X-Api-Key", model.credential());
        } else {
            headers.put("X-Api-App-ID", provider.appId());
            headers.put("X-Api-Access-Key", model.credential());
            headers.put("X-Api-App-Key", provider.appKey());
        }

        DoubaoRealtimeSession session = new DoubaoRealtimeSession(codec, config.toSessionConfig(), listener);
        WebSocket webSocket = connector.connect(URI.create(model.baseUrl()), Map.copyOf(headers), session).join();
        session.bind(webSocket);
        return session;
    }

    interface WebSocketConnector {
        CompletableFuture<WebSocket> connect(URI uri, Map<String, String> headers, WebSocket.Listener listener);
    }

    private record JdkWebSocketConnector(HttpClient client) implements WebSocketConnector {
        @Override
        public CompletableFuture<WebSocket> connect(URI uri, Map<String, String> headers,
                                                    WebSocket.Listener listener) {
            WebSocket.Builder builder = client.newWebSocketBuilder();
            headers.forEach(builder::header);
            return builder.buildAsync(uri, listener);
        }
    }

    private record ProviderConfig(String authType, String appId, String resourceId, String appKey) {
        private static ProviderConfig parse(String json) {
            JsonNode root = parseObject(json, "Doubao provider config");
            String authType = optionalText(root, "authType", "APP_ACCESS_KEY").trim().toUpperCase(java.util.Locale.ROOT);
            String resourceId = requiredText(root, "resourceId");
            return switch (authType) {
                case "API_KEY" -> new ProviderConfig(authType, null, resourceId, null);
                case "APP_ACCESS_KEY" -> new ProviderConfig(
                        authType, requiredText(root, "appId"), resourceId, requiredText(root, "appKey"));
                default -> throw new IllegalArgumentException("Unsupported Doubao authType: " + authType);
            };
        }
    }

    private record ModelConfig(String speaker, String botName, String speakingStyle,
                               int outputSampleRate, int recvTimeout, String inputMod,
                               String model, String instructions) {
        private static ModelConfig parse(String json, String instructions) {
            JsonNode root = parseObject(json, "Doubao model config");
            String speaker = requiredText(root, "speaker");
            String botName = optionalText(root, "botName", "doubao");
            String speakingStyle = optionalText(root, "speakingStyle", "");
            int outputSampleRate = optionalPositiveInt(root, "outputSampleRate", 24000);
            int recvTimeout = optionalPositiveInt(root, "recvTimeout", 120);
            String inputMod = optionalText(root, "inputMod", "audio");
            String model = optionalText(root, "model", null);
            return new ModelConfig(speaker, botName, speakingStyle, outputSampleRate,
                    recvTimeout, inputMod, model, instructions);
        }

        private DoubaoRealtimeCodec.SessionConfig toSessionConfig() {
            return new DoubaoRealtimeCodec.SessionConfig(botName, instructions, speakingStyle,
                    speaker, outputSampleRate, recvTimeout, inputMod, model);
        }
    }

    private static JsonNode parseObject(String json, String label) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        JsonNode root = JsonUtils.parseTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(label + " must be a JSON object");
        }
        return root;
    }

    private static String requiredText(JsonNode root, String field) {
        String value = optionalText(root, field, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Doubao config field must not be blank: " + field);
        }
        return value.trim();
    }

    private static String optionalText(JsonNode root, String field, String fallback) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Doubao config field must be text: " + field);
        }
        return value.textValue();
    }

    private static int optionalPositiveInt(JsonNode root, String field, int fallback) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isIntegralNumber() || value.asInt() <= 0) {
            throw new IllegalArgumentException("Doubao config field must be a positive integer: " + field);
        }
        return value.asInt();
    }
}
