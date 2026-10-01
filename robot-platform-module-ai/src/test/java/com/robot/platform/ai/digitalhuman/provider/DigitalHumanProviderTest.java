package com.robot.platform.ai.digitalhuman.provider;

import com.sun.net.httpserver.HttpServer;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class DigitalHumanProviderTest {
    HttpServer server;
    DigitalHumanProperties properties;
    LiveTalkingProvider provider;
    final List<Request> requests = new CopyOnWriteArrayList<>();
    volatile String override;
    volatile String sessionJson = "\"upstream-uuid\"";
    record Request(String path, String body, String type, String authorization) {}

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestURI().getPath(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1),
                    exchange.getRequestHeaders().getFirst("Content-Type"), exchange.getRequestHeaders().getFirst("Authorization")));
            String response = override != null ? override : switch (exchange.getRequestURI().getPath()) {
                case "/offer" -> "{\"sdp\":\"v=0\\r\\nanswer\",\"type\":\"answer\",\"sessionid\":" + sessionJson + "}";
                case "/is_speaking" -> "{\"code\":0,\"data\":true}";
                default -> "{\"code\":0}";
            };
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        properties = new DigitalHumanProperties();
        DigitalHumanProperties.Service service = new DigitalHumanProperties.Service();
        service.setEnabled(true);
        service.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
        service.setToken("server-only-secret");
        properties.getServices().put("default", service);
        provider = new LiveTalkingProvider(properties);
    }
    @AfterEach void cleanup() { server.stop(0); }

    @Test void negotiatesAvatarAndForwardsEchoAudioInterruptionWithoutLeakingSecrets() {
        var session = provider.open(new DigitalHumanRenderConfig("LIVETALKING", "default", "avatar_1"), "v=0\r\noffer");
        assertEquals("v=0\r\nanswer", session.answerSdp());
        var offer = JsonUtils.parseTree(requests.get(0).body());
        assertEquals("offer", offer.path("type").asText());
        assertEquals("avatar_1", offer.path("avatar").asText());
        session.speak("hello");
        assertEquals("echo", JsonUtils.parseTree(requests.get(1).body()).path("type").asText());
        assertEquals("upstream-uuid", JsonUtils.parseTree(requests.get(1).body()).path("sessionid").asText());
        session.audio(DigitalHumanAudioStream.wav(new byte[] {1, 2, 3, 4}));
        Request audio = requests.get(2);
        assertEquals("/humanaudio", audio.path());
        assertTrue(audio.type().startsWith("multipart/form-data; boundary="));
        assertTrue(audio.body().contains("name=\"sessionid\"\r\n\r\nupstream-uuid\r\n"));
        assertTrue(audio.body().contains("filename=\"speech.wav\""));
        assertTrue(audio.body().contains("RIFF"));
        session.interrupt();
        assertEquals("/interrupt_talk", requests.get(3).path());
        assertTrue(session.isSpeaking());
        assertTrue(requests.stream().allMatch(r -> "Bearer server-only-secret".equals(r.authorization())));
        DigitalHumanProviders registry = new DigitalHumanProviders(List.of(provider), properties);
        String clientConfig = JsonUtils.toJsonString(registry.describe("{\"rendering\":{\"provider\":\"LIVETALKING\"}}"));
        assertFalse(clientConfig.contains("secret"));
        assertFalse(clientConfig.contains("127.0.0.1"));
    }

    @Test void preservesNumericSessionIdsForOlderLiveTalking() {
        sessionJson = "12345";
        var session = provider.open(new DigitalHumanRenderConfig("LIVETALKING", "default", ""), "v=0");
        session.interrupt();
        assertTrue(JsonUtils.parseTree(requests.get(1).body()).path("sessionid").isIntegralNumber());
        assertFalse(JsonUtils.parseTree(requests.get(0).body()).has("avatar"));
    }

    @Test void rejectsUpstreamApplicationErrorsEvenWhenHttpStatusIs200() {
        override = "{\"code\":-1,\"msg\":\"internal credentials and hostname\"}";
        var error = assertThrows(IllegalStateException.class, () -> provider.open(new DigitalHumanRenderConfig("LIVETALKING", "default", ""), "v=0"));
        assertFalse(error.getMessage().contains("credentials"));
        override = "{}";
        assertThrows(IllegalStateException.class, () -> provider.open(new DigitalHumanRenderConfig("LIVETALKING", "default", ""), "v=0"));
    }

    @Test void builtinIsDefaultAndOnlyConfiguredProvidersCanBeSelected() {
        DigitalHumanProviders registry = new DigitalHumanProviders(List.of(provider), properties);
        assertEquals("BUILTIN", registry.describe(null).provider());
        assertEquals("BUILTIN", registry.describe("{\"legacyOption\":true}").provider());
        assertEquals("LIVETALKING", registry.describe("{\"rendering\":{\"provider\":\"livetalking\"}}").provider());
        for (String invalid : List.of("[]", "{\"rendering\":{\"provider\":42}}", "{\"rendering\":{\"provider\":\"UNKNOWN\"}}", "{\"rendering\":{\"provider\":\"LIVETALKING\",\"service\":\"http://evil\"}}", "{\"rendering\":{\"provider\":\"LIVETALKING\",\"avatarId\":\"../secret\"}}"))
            assertThrows(RuntimeException.class, () -> registry.validate(invalid));
        properties.getServices().get("default").setEnabled(false);
        assertTrue(registry.services().isEmpty());
        assertThrows(RuntimeException.class, () -> registry.validate("{\"rendering\":{\"provider\":\"LIVETALKING\"}}"));
    }
}
