package com.robot.platform.ai.model.config;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiModelNetworkConfigurationTest {
    @Test void directModeOverridesOnlyThisClientAndKeepsSystemProxyUnchanged() {
        ProxySelector original = ProxySelector.getDefault();
        var proxy = new ProxySelector() {
            public List<Proxy> select(URI uri) { return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 12345))); }
            public void connectFailed(URI uri, SocketAddress address, IOException error) { }
        };
        try {
            ProxySelector.setDefault(proxy);
            var config = new AiModelNetworkConfiguration();
            var direct = config.aiModelHttpClient("DIRECT");
            assertEquals(List.of(Proxy.NO_PROXY), direct.proxy().orElseThrow().select(URI.create("https://dashscope.aliyuncs.com")));
            assertSame(proxy, ProxySelector.getDefault());
            assertTrue(config.aiModelHttpClient("SYSTEM").proxy().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> config.aiModelHttpClient("invalid"));
        } finally { ProxySelector.setDefault(original); }
    }
}
