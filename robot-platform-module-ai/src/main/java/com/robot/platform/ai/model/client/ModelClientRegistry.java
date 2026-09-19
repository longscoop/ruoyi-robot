package com.robot.platform.ai.model.client;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class ModelClientRegistry {

    private final Map<ClientKey, Object> clients;

    public ModelClientRegistry(List<RealtimeVoiceClient> realtimeVoiceClients,
                               List<ChatModelClient> chatModelClients,
                               List<AsrClient> asrClients,
                               List<TtsClient> ttsClients) {
        Map<ClientKey, Object> index = new HashMap<>();
        registerAll(index, realtimeVoiceClients, client -> client.providerType(), client -> client.modelType());
        registerAll(index, chatModelClients, client -> client.providerType(), client -> client.modelType());
        registerAll(index, asrClients, client -> client.providerType(), client -> client.modelType());
        registerAll(index, ttsClients, client -> client.providerType(), client -> client.modelType());
        this.clients = Map.copyOf(index);
    }

    public Object require(String providerType, String modelType) {
        ClientKey key = ClientKey.of(providerType, modelType);
        Object client = clients.get(key);
        if (client == null) {
            throw new IllegalStateException("No model client registered for providerType="
                    + key.providerType() + ", modelType=" + key.modelType());
        }
        return client;
    }

    public RealtimeVoiceClient requireRealtimeVoice(String providerType) {
        return requireTyped(providerType, "REALTIME_S2S", RealtimeVoiceClient.class);
    }

    public ChatModelClient requireChat(String providerType) {
        return requireTyped(providerType, "CHAT", ChatModelClient.class);
    }

    public AsrClient requireAsr(String providerType) {
        return requireTyped(providerType, "ASR", AsrClient.class);
    }

    public TtsClient requireTts(String providerType) {
        return requireTyped(providerType, "TTS", TtsClient.class);
    }

    private <T> T requireTyped(String providerType, String modelType, Class<T> expectedType) {
        Object client = require(providerType, modelType);
        if (!expectedType.isInstance(client)) {
            throw new IllegalStateException("Registered model client has unexpected type for providerType="
                    + normalize(providerType, "providerType") + ", modelType=" + modelType);
        }
        return expectedType.cast(client);
    }

    private static <T> void registerAll(Map<ClientKey, Object> index,
                                        List<T> clients,
                                        java.util.function.Function<T, String> providerType,
                                        java.util.function.Function<T, String> modelType) {
        if (clients == null) {
            return;
        }
        for (T client : clients) {
            Objects.requireNonNull(client, "model client");
            ClientKey key = ClientKey.of(providerType.apply(client), modelType.apply(client));
            Object existing = index.putIfAbsent(key, client);
            if (existing != null) {
                throw new IllegalStateException("Duplicate model client registration for providerType="
                        + key.providerType() + ", modelType=" + key.modelType());
            }
        }
    }

    private static String normalize(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private record ClientKey(String providerType, String modelType) {
        private static ClientKey of(String providerType, String modelType) {
            return new ClientKey(normalize(providerType, "providerType"), normalize(modelType, "modelType"));
        }
    }
}
