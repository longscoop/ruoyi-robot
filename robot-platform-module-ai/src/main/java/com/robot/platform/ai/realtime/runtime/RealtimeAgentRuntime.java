package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.realtime.gateway.AiRealtimeWebSocketHandler;
import com.robot.platform.ai.realtime.protocol.RealtimeAudioFormat;
import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.security.ApiAudience;
import org.springframework.web.socket.CloseStatus;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

public class RealtimeAgentRuntime implements AiRealtimeWebSocketHandler.Runtime {

    public enum State {
        CONNECTING,
        READY,
        USER_SPEAKING,
        ASSISTANT_RESPONDING,
        CLOSED
    }

    public record CloseReason(int code, String reason) {
    }

    private final DeviceSession deviceSession;
    private final AiAgentRobotBindingService bindingService;
    private final AiAgentService agentService;
    private final RealtimeModelRouter router;

    private State state = State.CONNECTING;
    private AiAgentConfig agentConfig;
    private RealtimeRoute route;
    private RealtimeClientEvent.CandidateIdentity candidateIdentity;
    private RealtimeAudioFormat audioFormat;
    private CloseReason closeReason;

    public RealtimeAgentRuntime(DeviceSession deviceSession,
                                AiAgentRobotBindingService bindingService,
                                AiAgentService agentService,
                                RealtimeModelRouter router) {
        this.deviceSession = requireTrustedDeviceSession(deviceSession);
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService");
        this.agentService = Objects.requireNonNull(agentService, "agentService");
        this.router = Objects.requireNonNull(router, "router");
    }

    @Override
    public synchronized void acceptControl(RealtimeClientEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("realtime client event must not be null");
        }
        if (state == State.CLOSED) {
            throw new IllegalStateException("Realtime runtime is closed");
        }

        if (event instanceof RealtimeClientEvent.SessionStartEvent sessionStart) {
            start(sessionStart);
            return;
        }
        if (event instanceof RealtimeClientEvent.SpeechStartedEvent) {
            transition(State.READY, State.USER_SPEAKING, "input.speech_started");
            return;
        }
        if (event instanceof RealtimeClientEvent.SpeechStoppedEvent) {
            transition(State.USER_SPEAKING, State.ASSISTANT_RESPONDING, "input.speech_stopped");
            return;
        }
        if (event instanceof RealtimeClientEvent.SessionCloseEvent sessionClose) {
            close(new CloseReason(1000,
                    sessionClose.reason() == null || sessionClose.reason().isBlank()
                            ? "CLIENT_CLOSE" : sessionClose.reason()));
            return;
        }

        throw new IllegalArgumentException("Unsupported realtime client event: " + event.type());
    }

    @Override
    public synchronized void acceptAudio(ByteBuffer pcm) {
        if (state != State.USER_SPEAKING) {
            throw new IllegalStateException("Binary audio is only accepted while user is speaking");
        }
        if (pcm == null) {
            throw new IllegalArgumentException("PCM buffer must not be null");
        }
        // Transport ownership remains with the caller. Provider forwarding is added in later runtime tasks.
        pcm.remaining();
    }

    public synchronized void completeAssistantResponse() {
        transition(State.ASSISTANT_RESPONDING, State.READY, "assistant.done");
    }

    public synchronized RealtimeRoute routeForTurn(boolean requiresChatOnlyCapability,
                                                   boolean requiresToolPath,
                                                   boolean nativeToolCallingSupported) {
        requireStarted();
        RealtimeModelRouter.ModelCapabilities configured =
                RealtimeModelRouter.ModelCapabilities.configured(agentConfig);
        RealtimeModelRouter.ModelCapabilities capabilities =
                new RealtimeModelRouter.ModelCapabilities(
                        configured.nativeRealtimeSupported(),
                        configured.cascadeSupported(),
                        nativeToolCallingSupported,
                        requiresChatOnlyCapability,
                        requiresToolPath);
        return router.route(agentConfig, capabilities);
    }

    @Override
    public void close(CloseStatus reason) {
        if (reason == null) {
            close(new CloseReason(1011, "UNKNOWN"));
            return;
        }
        close(new CloseReason(reason.getCode(), reason.getReason()));
    }

    public synchronized void close(CloseReason reason) {
        if (state == State.CLOSED) {
            return;
        }
        closeReason = reason == null ? new CloseReason(1011, "UNKNOWN") : reason;
        state = State.CLOSED;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized AiAgentConfig agentConfig() {
        return agentConfig;
    }

    public synchronized RealtimeRoute route() {
        return route;
    }

    public synchronized RealtimeClientEvent.CandidateIdentity candidateIdentity() {
        return candidateIdentity;
    }

    public synchronized RealtimeAudioFormat audioFormat() {
        return audioFormat;
    }

    public Long effectiveMemberId() {
        // Candidate identity is intentionally untrusted until Plan 3 validation is available.
        return null;
    }

    public synchronized CloseReason closeReason() {
        return closeReason;
    }

    public DeviceSession deviceSession() {
        return deviceSession;
    }

    private void start(RealtimeClientEvent.SessionStartEvent event) {
        requireState(State.CONNECTING, "session.start");
        if (event.agentCode() == null || event.agentCode().isBlank()) {
            throw new IllegalArgumentException("session.start agentCode must not be blank");
        }
        if (event.audio() == null) {
            throw new IllegalArgumentException("session.start audio format must not be null");
        }

        AiAgentConfig resolved = withTrustedTenant(() -> {
            AiAgentDO agent = bindingService.requireAgentForRobot(
                    deviceSession.tenantId(), deviceSession.robotId(), event.agentCode());
            if (agent == null || agent.getId() == null) {
                throw new IllegalStateException("Resolved agent is missing");
            }
            AiAgentConfig config = agentService.getResolvedConfig(deviceSession.tenantId(), agent.getId());
            if (config == null
                    || config.tenantId() != deviceSession.tenantId()
                    || config.agentId() != agent.getId()) {
                throw new IllegalStateException("Resolved agent configuration does not match trusted device session");
            }
            return config;
        });

        agentConfig = resolved;
        candidateIdentity = event.identity();
        audioFormat = event.audio();
        route = router.route(resolved, RealtimeModelRouter.ModelCapabilities.configured(resolved));
        state = State.READY;
    }

    private void transition(State expected, State next, String event) {
        requireState(expected, event);
        state = next;
    }

    private void requireStarted() {
        if (agentConfig == null || route == null || state == State.CONNECTING) {
            throw new IllegalStateException("Realtime session has not started");
        }
        if (state == State.CLOSED) {
            throw new IllegalStateException("Realtime runtime is closed");
        }
    }

    private void requireState(State expected, String event) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Invalid realtime transition for " + event + ": " + state + " -> expected " + expected);
        }
    }

    private <T> T withTrustedTenant(Supplier<T> supplier) {
        Long previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(deviceSession.tenantId());
            TenantContextHolder.setIgnore(false);
            return supplier.get();
        } finally {
            TenantContextHolder.clear();
            if (previousTenant != null) {
                TenantContextHolder.setTenantId(previousTenant);
            }
            if (previousIgnore) {
                TenantContextHolder.setIgnore(true);
            }
        }
    }

    private static DeviceSession requireTrustedDeviceSession(DeviceSession session) {
        if (session == null
                || session.audience() != ApiAudience.DEVICE
                || session.tenantId() <= 0
                || session.deviceId() <= 0
                || session.robotId() <= 0) {
            throw new IllegalArgumentException("Trusted DEVICE session is required");
        }
        return session;
    }
}
