package com.robot.platform.ai.model.client;

/** One response, one connection. AudioDone means all submitted text has finished. */
public interface TtsSession extends TtsStream {
    void appendText(String text);
    void finishInput();
}
