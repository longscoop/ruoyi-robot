package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.memory.identity.ConversationIdentityResolver;
import com.robot.platform.ai.memory.pipeline.MemoryPipeline;
import com.robot.platform.ai.memory.context.MemoryContextBuilder;
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
    private final ConversationIdentityResolver identityResolver;
    private final MemoryPipeline memoryPipeline;
    private final MemoryContextBuilder memoryContextBuilder;
    private final ConcurrentMap<String, RealtimeAgentRuntime> runtimes = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeAgentRuntimeManager(AiAgentRobotBindingService bindingService,
                                       AiAgentService agentService,
                                       RealtimeModelRouter router,
                                       ResolvedModelResolver modelResolver,
                                       ModelClientRegistry clientRegistry,
                                       ConversationIdentityResolver identityResolver,
                                       MemoryPipeline memoryPipeline,
                                       MemoryContextBuilder memoryContextBuilder) {
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService");
        this.agentService = Objects.requireNonNull(agentService, "agentService");
        this.router = Objects.requireNonNull(router, "router");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver");
        this.clientRegistry = Objects.requireNonNull(clientRegistry, "clientRegistry");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        this.memoryPipeline = Objects.requireNonNull(memoryPipeline, "memoryPipeline");
        this.memoryContextBuilder = Objects.requireNonNull(memoryContextBuilder, "memoryContextBuilder");
    }

    public RealtimeAgentRuntimeManager(AiAgentRobotBindingService bindingService,
                                       AiAgentService agentService,
                                       RealtimeModelRouter router) {
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService");
        this.agentService = Objects.requireNonNull(agentService, "agentService");
        this.router = Objects.requireNonNull(router, "router");
        this.modelResolver = null;
        this.clientRegistry = null;
        this.identityResolver = null;
        this.memoryPipeline = null;
        this.memoryContextBuilder = null;
    }

    @Override
    public void open(String webSocketSessionId, DeviceSession deviceSession) {
        requireSessionId(webSocketSessionId);
        RealtimeAgentRuntime runtime = new RealtimeAgentRuntime(
                webSocketSessionId, deviceSession, bindingService, agentService, router,
                modelResolver, clientRegistry, identityResolver, memoryPipeline, memoryContextBuilder);
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
