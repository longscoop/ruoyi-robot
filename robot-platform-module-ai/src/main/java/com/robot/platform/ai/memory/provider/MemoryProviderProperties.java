package com.robot.platform.ai.memory.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "robot.ai.memory")
public class MemoryProviderProperties {
    private int saveMessageThreshold = 6;
    private int contextMaxChars = 1200;
    private String localDirectory = "data/ai-memory";
    private int localMaxEntries = 40;
    private int queryDeadlineMillis = 300;
    private Remote mem0 = new Remote("https://api.mem0.ai");
    private Remote powermem = new Remote("http://127.0.0.1:8010");
    private Map<Long, Remote> mem0Tenants = new HashMap<>();
    private Map<Long, Remote> powermemTenants = new HashMap<>();
    public Remote remote(String name, long tenant) {
        return name.equals("mem0ai") ? mem0Tenants.getOrDefault(tenant, mem0)
                : powermemTenants.getOrDefault(tenant, powermem);
    }
    @Data
    public static class Remote {
        private String baseUrl;
        private String apiKey = "";
        private boolean enabled;
        private int queryTimeoutMillis = 1200;
        private int saveTimeoutMillis = 30000;
        private String searchPath = "/v3/memories/search/";
        private String addPath = "/v3/memories/add/";
        public Remote() { }
        public Remote(String baseUrl) { this.baseUrl = baseUrl; }
    }
}
