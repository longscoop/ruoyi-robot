package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.agent.service.AiAgentRobotBindingService;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.model.client.ModelClientRegistry;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.RealtimeVoiceClient;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.ai.realtime.gateway.AiRealtimeWebSocketHandler;
import com.robot.platform.ai.realtime.protocol.RealtimeAudioFormat;
import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.ai.realtime.protocol.RealtimeServerEvent;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.security.ApiAudience;
import org.springframework.web.socket.CloseStatus;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

public class RealtimeAgentRuntime implements AiRealtimeWebSocketHandler.Runtime {

    private static final RealtimeAudioFormat PROVIDER_OUTPUT_AUDIO =
            new RealtimeAudioFormat("PCM_S16LE", 24000, 1);

    public enum State {
        CONNECTING,
        READY,
        USER_SPEAKING,
        ASSISTANT_RESPONDING,
        CLOSED
    }

    public record CloseReason(int code, String reason) {
    }

    private final String sessionId;
    private final DeviceSession deviceSession;
    private final AiAgentRobotBindingService bindingService;
    private final AiAgentService agentService;
    private final RealtimeModelRouter router;
    private final ResolvedModelResolver modelResolver;
    private final ModelClientRegistry clientRegistry;

    private State state = State.CONNECTING;
    private AiAgentConfig agentConfig;
    private RealtimeRoute route;
    private RealtimeClientEvent.CandidateIdentity candidateIdentity;
    private RealtimeAudioFormat audioFormat;
    private CloseReason closeReason;
    private RealtimeRuntimeOutput output;
    private RealtimeProviderSession providerSession;
    private long turnSequence;
    private String currentTurnId;
    private boolean assistantAudioStarted;

    public RealtimeAgentRuntime(DeviceSession deviceSession,
                                AiAgentRobotBindingService bindingService,
                                AiAgentService agentService,
                                RealtimeModelRouter router) {
        this("standalone", deviceSession, bindingService, agentService, router, null, null);
    }

    public RealtimeAgentRuntime(String sessionId,
                                DeviceSession deviceSession,
                                AiAgentRobotBindingService bindingService,
                                AiAgentService agentService,
                                RealtimeModelRouter router,
                                ResolvedModelResolver modelResolver,
                                ModelClientRegistry clientRegistry) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        this.sessionId = sessionId;
        this.deviceSession = requireTrustedDeviceSession(deviceSession);
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService");
        this.agentService = Objects.requireNonNull(agentService, "agentService");
        this.router = Objects.requireNonNull(router, "router");
        this.modelResolver = modelResolver;
        this.clientRegistry = clientRegistry;
    }

    public synchronized void attachOutput(RealtimeRuntimeOutput output) {
        Objects.requireNonNull(output, "output");
        if (this.output != null) {
            throw new IllegalStateException("Realtime runtime output is already attached");
        }
        this.output = output;
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
            speechStarted();
            return;
        }
        if (event instanceof RealtimeClientEvent.SpeechStoppedEvent) {
            speechStopped();
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
        if (providerSession != null) {
            providerSession.appendAudio(pcm.asReadOnlyBuffer());
        }
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
        if (providerSession != null) {
            providerSession.close();
            providerSession = null;
        }
        if (output != null) {
            output.sendEvent(new RealtimeServerEvent.SessionClosedEvent(sessionId, closeReason.reason()));
        }
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
        if (modelResolver != null && clientRegistry != null) {
            if (route.mode() == RealtimeRoute.Mode.NATIVE) {
                openNativeProvider();
            } else if (route.mode() == RealtimeRoute.Mode.CASCADE) {
                openCascadeProvider();
            }
        }
        state = State.READY;
        if (output != null) {
            output.sendEvent(new RealtimeServerEvent.SessionCreatedEvent(sessionId, route.mode().name()));
        }
    }

    private void openNativeProvider() {
        Long modelId = route.realtimeModelId();
        if (modelId == null) {
            throw new IllegalStateException("Native route is missing realtime model id");
        }
        ResolvedModel resolvedModel = withTrustedTenant(
                () -> modelResolver.resolve(deviceSession.tenantId(), modelId));
        if (resolvedModel.modelId() != modelId) {
            throw new IllegalStateException("Resolved model does not match realtime route");
        }
        resolvedModel = resolvedModel.withRealtimeSession(agentConfig.systemPrompt(), agentConfig.voiceConfigJson());
        RealtimeVoiceClient client = clientRegistry.requireRealtimeVoice(resolvedModel.providerType());
        providerSession = client.open(resolvedModel, this::onProviderEvent);
    }

    private void openCascadeProvider() {
        Long asrModelId = route.asrModelId();
        Long conversationModelId = route.conversationModelId();
        Long ttsModelId = route.ttsModelId();
        if (asrModelId == null || conversationModelId == null || ttsModelId == null) {
            throw new IllegalStateException("Cascade route is missing ASR/Chat/TTS model ids");
        }

        CascadeModels models = withTrustedTenant(() -> new CascadeModels(
                modelResolver.resolve(deviceSession.tenantId(), asrModelId),
                modelResolver.resolve(deviceSession.tenantId(), conversationModelId),
                modelResolver.resolve(deviceSession.tenantId(), ttsModelId)));
        if (models.asr().modelId() != asrModelId
                || models.chat().modelId() != conversationModelId
                || models.tts().modelId() != ttsModelId) {
            throw new IllegalStateException("Resolved models do not match cascade route");
        }
        providerSession = new CascadeRealtimePipeline(
                models.asr(), models.chat(), models.tts(),
                agentConfig.systemPrompt(), clientRegistry, this::onProviderEvent);
    }

    private record CascadeModels(ResolvedModel asr, ResolvedModel chat, ResolvedModel tts) {
    }

    private void speechStarted() {
        requireState(State.READY, "input.speech_started");
        currentTurnId = "turn-" + (++turnSequence);
        assistantAudioStarted = false;
        state = State.USER_SPEAKING;
        if (providerSession != null) {
            providerSession.speechStarted();
        }
    }

    private void speechStopped() {
        requireState(State.USER_SPEAKING, "input.speech_stopped");
        state = State.ASSISTANT_RESPONDING;
        if (providerSession != null) {
            providerSession.speechStopped();
        }
    }

    private synchronized void onProviderEvent(ProviderEvent event) {
        if (event == null || state == State.CLOSED || output == null) {
            return;
        }
        if (event instanceof ProviderEvent.TranscriptDelta value) {
            output.sendEvent(new RealtimeServerEvent.InputTranscriptDeltaEvent(
                    sessionId, requireTurnId(), value.text()));
        } else if (event instanceof ProviderEvent.TranscriptDone value) {
            output.sendEvent(new RealtimeServerEvent.InputTranscriptDoneEvent(
                    sessionId, requireTurnId(), value.text()));
        } else if (event instanceof ProviderEvent.TextDelta value) {
            output.sendEvent(new RealtimeServerEvent.AssistantTextDeltaEvent(
                    sessionId, requireTurnId(), value.text()));
        } else if (event instanceof ProviderEvent.TextDone value) {
            output.sendEvent(new RealtimeServerEvent.AssistantTextDoneEvent(
                    sessionId, requireTurnId(), value.text()));
        } else if (event instanceof ProviderEvent.AudioDelta value) {
            if (!assistantAudioStarted) {
                output.sendEvent(new RealtimeServerEvent.AssistantAudioStartedEvent(
                        sessionId, requireTurnId(), PROVIDER_OUTPUT_AUDIO));
                assistantAudioStarted = true;
            }
            output.sendAudio(value.audio());
        } else if (event instanceof ProviderEvent.AudioDone) {
            output.sendEvent(new RealtimeServerEvent.AssistantAudioDoneEvent(sessionId, requireTurnId()));
            output.sendEvent(new RealtimeServerEvent.AssistantDoneEvent(sessionId, requireTurnId()));
            if (state == State.ASSISTANT_RESPONDING) {
                state = State.READY;
            }
        } else if (event instanceof ProviderEvent.ToolCall value) {
            output.sendEvent(new RealtimeServerEvent.ToolStartedEvent(
                    sessionId, requireTurnId(), value.id(), value.name()));
        } else if (event instanceof ProviderEvent.ProviderError value) {
            output.sendEvent(new RealtimeServerEvent.SessionErrorEvent(
                    sessionId, value.code(), value.message()));
        }
    }

    private String requireTurnId() {
        if (currentTurnId == null) {
            throw new IllegalStateException("Provider emitted turn-scoped output before user turn started");
        }
        return currentTurnId;
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
