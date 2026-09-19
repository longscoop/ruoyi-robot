package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.model.client.ModelClientRegistry;
import com.robot.platform.ai.realtime.gateway.AiRealtimeWebSocketHandler;
import com.robot.platform.device.auth.service.DeviceSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RealtimeAgentRuntimeManager implements AiRealtimeWebSocketHandler.RuntimeManager {

    private final AiAgentRobotBindingService bindingService;
    private final AiAgentService agentService;
    private final RealtimeModelRouter router;
    private final ResolvedModelResolver modelResolver;
    private final ModelClientRegistry clientRegistry;
    private final ConcurrentMap<String, RealtimeAgentRuntime> runtimes = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeAgentRuntimeManager(AiAgentRobotBindingService bindingService,
                                       AiAgentService agentService,
                                       RealtimeModelRouter router,
                                       ResolvedModelResolver modelResolver,
                                       ModelClientRegistry clientRegistry) {
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService");
        this.agentService = Objects.requireNonNull(agentService, "agentService");
        this.router = Objects.requireNonNull(router, "router");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver");
        this.clientRegistry = Objects.requireNonNull(clientRegistry, "clientRegistry");
    }

    public RealtimeAgentRuntimeManager(AiAgentRobotBindingService bindingService,
                                       AiAgentService agentService,
                                       RealtimeModelRouter router) {
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService");
        this.agentService = Objects.requireNonNull(agentService, "agentService");
        this.router = Objects.requireNonNull(router, "router");
        this.modelResolver = null;
        this.clientRegistry = null;
    }

    @Override
    public void open(String webSocketSessionId, DeviceSession deviceSession) {
        requireSessionId(webSocketSessionId);
        RealtimeAgentRuntime runtime = new RealtimeAgentRuntime(
                webSocketSessionId, deviceSession, bindingService, agentService, router, modelResolver, clientRegistry);
        RealtimeAgentRuntime existing = runtimes.putIfAbsent(webSocketSessionId, runtime);
        if (existing != null) {
            throw new IllegalStateException("Realtime runtime already exists for WebSocket session");
        }
    }

    @Override
    public void attachOutput(String webSocketSessionId, RealtimeRuntimeOutput output) {
        require(webSocketSessionId).attachOutput(output);
    }

    @Override
    public RealtimeAgentRuntime require(String webSocketSessionId) {
        requireSessionId(webSocketSessionId);
        RealtimeAgentRuntime runtime = runtimes.get(webSocketSessionId);
        if (runtime == null) {
            throw new IllegalStateException("Realtime runtime does not exist for WebSocket session");
        }
        return runtime;
    }

    @Override
    public RealtimeAgentRuntime remove(String webSocketSessionId) {
        if (webSocketSessionId == null || webSocketSessionId.isBlank()) {
            return null;
        }
        return runtimes.remove(webSocketSessionId);
    }

    private static void requireSessionId(String webSocketSessionId) {
        if (webSocketSessionId == null || webSocketSessionId.isBlank()) {
            throw new IllegalArgumentException("WebSocket session id must not be blank");
        }
    }
}
