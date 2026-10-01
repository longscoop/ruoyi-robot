package com.robot.platform.ai.digitalhuman.provider;

import org.springframework.stereotype.Service;
import java.util.*;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class DigitalHumanProviders {
    private final Map<String, DigitalHumanProvider> providers = new HashMap<>();
    private final DigitalHumanProperties properties;

    public DigitalHumanProviders(List<DigitalHumanProvider> implementations, DigitalHumanProperties properties) {
        this.properties = properties;
        for (DigitalHumanProvider provider : implementations) {
            if (providers.putIfAbsent(provider.id(), provider) != null)
                throw new IllegalStateException("Duplicate digital human provider: " + provider.id());
        }
    }

    public DigitalHumanRenderConfig validate(String json) {
        DigitalHumanRenderConfig config = DigitalHumanRenderConfig.parse(json);
        if ("BUILTIN".equals(config.provider())) return config;
        DigitalHumanProperties.Service service = properties.getServices().get(config.service());
        if (!providers.containsKey(config.provider()) || service == null || !service.isEnabled()
                || !config.provider().equals(service.getProvider()))
            throw invalidParamException("数字人渲染服务未启用或不存在");
        return config;
    }

    public ClientConfig describe(String json) {
        DigitalHumanRenderConfig config = validate(json);
        return new ClientConfig(config.provider(), "BUILTIN".equals(config.provider()) ? List.of() : properties.getIceServers());
    }

    public DigitalHumanProvider.Session open(String json, String sdp) {
        DigitalHumanRenderConfig config = validate(json);
        if (sdp == null || !sdp.startsWith("v=0") || sdp.length() > 65536)
            throw invalidParamException("Invalid WebRTC offer SDP");
        DigitalHumanProvider provider = providers.get(config.provider());
        if (provider == null) throw invalidParamException("当前数字人不使用远程视频渲染");
        return provider.open(config, sdp);
    }

    public List<ServiceOption> services() {
        return properties.getServices().entrySet().stream()
                .filter(e -> e.getValue().isEnabled() && providers.containsKey(e.getValue().getProvider()))
                .map(e -> new ServiceOption(e.getKey(), e.getValue().getName(), e.getValue().getProvider())).toList();
    }

    public record ClientConfig(String provider, List<DigitalHumanProperties.IceServer> iceServers) {}
    public record ServiceOption(String id, String name, String provider) {}
}
