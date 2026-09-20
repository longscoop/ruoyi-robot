package com.robot.platform.ai.model.client;

import com.robot.platform.ai.model.client.event.ProviderEvent;

import java.nio.ByteBuffer;
import java.util.Objects;

public interface RealtimeVoiceClient {

    String providerType();

    default String modelType() {
        return "REALTIME_S2S";
    }

    RealtimeProviderSession open(ResolvedModel model, RealtimeProviderListener listener);

    default RealtimeProviderSession openTurnAware(ResolvedModel model, RealtimeTurnListener listener) {
        Objects.requireNonNull(listener, "listener");
        TurnAwareBridge bridge = new TurnAwareBridge(listener);
        RealtimeProviderSession delegate = open(model, bridge::onEvent);
        return new RealtimeProviderSession() {
            @Override
            public void beginTurn(String turnId, long generation) {
                bridge.beginTurn(turnId, generation);
                delegate.beginTurn(turnId, generation);
            }

            @Override public void appendAudio(ByteBuffer pcm) { delegate.appendAudio(pcm); }
            @Override public void speechStarted() { delegate.speechStarted(); }
            @Override public void speechStopped() { delegate.speechStopped(); }
            @Override public void cancelCurrentResponse() { delegate.cancelCurrentResponse(); }
            @Override public void close() { delegate.close(); }
        };
    }

    final class TurnAwareBridge {
        private final RealtimeTurnListener listener;
        private volatile String turnId;
        private volatile long generation;

        private TurnAwareBridge(RealtimeTurnListener listener) {
            this.listener = listener;
        }

        private void beginTurn(String turnId, long generation) {
            this.turnId = turnId;
            this.generation = generation;
        }

        private void onEvent(ProviderEvent event) {
            listener.onEvent(turnId, generation, event);
        }
    }
}
