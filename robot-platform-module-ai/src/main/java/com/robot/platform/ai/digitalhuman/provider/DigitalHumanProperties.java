package com.robot.platform.ai.digitalhuman.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;

@Data
@Component
@ConfigurationProperties(prefix = "robot.ai.digital-human")
public class DigitalHumanProperties {
    private Map<String, Service> services = new LinkedHashMap<>();
    private List<IceServer> iceServers = new ArrayList<>();

    @Data public static class Service {
        private boolean enabled;
        private String name = "LiveTalking";
        private String provider = "LIVETALKING";
        private String url;
        /** Optional authentication on an upstream reverse proxy; never sent to clients. */
        private String token;
        private int timeoutSeconds = 30;
    }

    /** ICE credentials, if configured, are intentionally delivered to WebRTC clients. */
    public record IceServer(List<String> urls, String username, String credential) {}
}
