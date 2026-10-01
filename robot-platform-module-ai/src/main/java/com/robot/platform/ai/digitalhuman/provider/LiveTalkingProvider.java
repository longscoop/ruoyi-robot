package com.robot.platform.ai.digitalhuman.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Adapter for upstream /offer, /human, /humanaudio, /interrupt_talk and /is_speaking. */
@Component
public class LiveTalkingProvider implements DigitalHumanProvider {
    private final DigitalHumanProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public LiveTalkingProvider(DigitalHumanProperties properties) { this.properties = properties; }
    @Override public String id() { return "LIVETALKING"; }

    @Override public Session open(DigitalHumanRenderConfig config, String offerSdp) {
        DigitalHumanProperties.Service service = properties.getServices().get(config.service());
        if (service == null || !service.isEnabled()) throw unavailable();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sdp", offerSdp);
        body.put("type", "offer");
        if (!config.avatarId().isBlank()) body.put("avatar", config.avatarId());
        JsonNode answer = json(service, "/offer", body);
        JsonNode session = answer.get("sessionid");
        if (!"answer".equals(answer.path("type").asText()) || !answer.path("sdp").asText().startsWith("v=0")
                || session == null || (!session.isTextual() && !session.isIntegralNumber())
                || session.asText().isBlank()) throw unavailable();
        // Preserve numeric IDs for older releases; current upstream uses UUID strings.
        Object sessionId = session.isIntegralNumber() ? session.longValue() : session.asText();
        return new Session() {
            public String answerSdp() { return answer.path("sdp").asText(); }
            public void speak(String text) { json(service, "/human", Map.of("sessionid", sessionId, "type", "echo", "text", text)); }
            public void interrupt() { json(service, "/interrupt_talk", Map.of("sessionid", sessionId)); }
            public boolean isSpeaking() {
                JsonNode result = json(service, "/is_speaking", Map.of("sessionid", sessionId)).path("data");
                if (!result.isBoolean()) throw unavailable();
                return result.asBoolean();
            }
            public void audio(byte[] wav) {
                String boundary = "robot-" + UUID.randomUUID();
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"sessionid\"\r\n\r\n"
                        + sessionId + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                        + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                body.writeBytes(wav);
                body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                post(service, "/humanaudio", "multipart/form-data; boundary=" + boundary, body.toByteArray());
            }
        };
    }

    private JsonNode json(DigitalHumanProperties.Service service, String path, Object body) {
        return post(service, path, "application/json", JsonUtils.toJsonString(body).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode post(DigitalHumanProperties.Service service, String path, String type, byte[] body) {
        try {
            URI uri = URI.create(service.getUrl().replaceAll("/+$", "") + path);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw unavailable();
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, Math.min(service.getTimeoutSeconds(), 120))))
                    .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (service.getToken() != null && !service.getToken().isBlank())
                request.header("Authorization", "Bearer " + service.getToken());
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
            JsonNode result = JsonUtils.parseTree(response.body());
            if (result == null || !result.isObject() || (result.has("code") && result.path("code").asInt(-1) != 0)
                    || (!"/offer".equals(path) && !result.has("code"))) throw unavailable();
            return result;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception error) {
            // Do not leak upstream URLs, credentials or server error bodies to clients.
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("LiveTalking 服务不可用，请检查服务配置、形象和并发上限");
    }
}
