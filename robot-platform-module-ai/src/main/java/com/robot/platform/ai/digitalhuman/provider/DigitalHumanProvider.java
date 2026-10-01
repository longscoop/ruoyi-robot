package com.robot.platform.ai.digitalhuman.provider;

/** Rendering is independent of the agent's ASR / LLM / TTS providers. */
public interface DigitalHumanProvider {
    String id();
    Session open(DigitalHumanRenderConfig config, String offerSdp);

    interface Session extends AutoCloseable {
        String answerSdp();
        void speak(String text);
        void audio(byte[] wav);
        void interrupt();
        boolean isSpeaking();
        // LiveTalking releases its GPU session when the client closes its peer connection.
        @Override default void close() { interrupt(); }
    }
}
