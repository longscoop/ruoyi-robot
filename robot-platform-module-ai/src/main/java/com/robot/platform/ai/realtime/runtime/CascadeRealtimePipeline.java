package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.model.client.AsrClient;
import com.robot.platform.ai.model.client.AsrSession;
import com.robot.platform.ai.model.client.ChatModelClient;
import com.robot.platform.ai.model.client.ChatRequest;
import com.robot.platform.ai.model.client.ChatStream;
import com.robot.platform.ai.model.client.ModelClientRegistry;
import com.robot.platform.ai.model.client.ResolvedModel;
import com.robot.platform.ai.model.client.RealtimeProviderListener;
import com.robot.platform.ai.model.client.RealtimeProviderSession;
import com.robot.platform.ai.model.client.TtsClient;
import com.robot.platform.ai.model.client.TtsRequest;
import com.robot.platform.ai.model.client.TtsStream;
import com.robot.platform.ai.model.client.event.ProviderEvent;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class CascadeRealtimePipeline implements RealtimeProviderSession {

    private static final int MAX_TTS_QUEUE = 16;
    private static final int MAX_UNSPOKEN_CHARS = 64;

    private final ResolvedModel asrModel;
    private final ResolvedModel chatModel;
    private final ResolvedModel ttsModel;
    private final String systemPrompt;
    private final AsrClient asrClient;
    private final ChatModelClient chatClient;
    private final TtsClient ttsClient;
    private final RealtimeProviderListener listener;

    private final StringBuilder textBuffer = new StringBuilder();
    private final Deque<String> ttsQueue = new ArrayDeque<>();

    private AsrSession asrSession;
    private ChatStream chatStream;
    private TtsStream ttsStream;
    private boolean speaking;
    private boolean cancelled;
    private boolean chatDone;
    private boolean audioDoneEmitted;
    private boolean textDoneForwarded;
    private boolean closed;

    public CascadeRealtimePipeline(ResolvedModel asrModel,
                                   ResolvedModel chatModel,
                                   ResolvedModel ttsModel,
                                   String systemPrompt,
                                   ModelClientRegistry registry,
                                   RealtimeProviderListener listener) {
        this.asrModel = Objects.requireNonNull(asrModel, "asrModel");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.ttsModel = Objects.requireNonNull(ttsModel, "ttsModel");
        this.systemPrompt = systemPrompt;
        Objects.requireNonNull(registry, "registry");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.asrClient = registry.requireAsr(asrModel.providerType());
        this.chatClient = registry.requireChat(chatModel.providerType());
        this.ttsClient = registry.requireTts(ttsModel.providerType());
    }

    @Override
    public synchronized void speechStarted() {
        requireOpen();
        if (speaking) {
            throw new IllegalStateException("Cascade user speech already started");
        }
        resetTurn();
        speaking = true;
        asrSession = asrClient.open(asrModel, this::onAsrEvent);
        asrSession.speechStarted();
    }

    @Override
    public synchronized void appendAudio(ByteBuffer pcm) {
        requireOpen();
        if (!speaking || asrSession == null) {
            throw new IllegalStateException("Cascade audio requires active user speech");
        }
        asrSession.appendAudio(pcm);
    }

    @Override
    public synchronized void speechStopped() {
        requireOpen();
        if (!speaking || asrSession == null) {
            throw new IllegalStateException("Cascade user speech has not started");
        }
        speaking = false;
        asrSession.speechStopped();
    }

    @Override
    public synchronized void cancelCurrentResponse() {
        if (closed) {
            return;
        }
        cancelled = true;
        speaking = false;
        textBuffer.setLength(0);
        ttsQueue.clear();
        if (asrSession != null) {
            asrSession.cancel();
        }
        if (chatStream != null) {
            chatStream.cancel();
        }
        if (ttsStream != null) {
            ttsStream.cancel();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        cancelCurrentResponse();
        closed = true;
    }

    private synchronized void onAsrEvent(ProviderEvent event) {
        if (cancelled || closed) {
            return;
        }
        if (event instanceof ProviderEvent.TranscriptDelta) {
            listener.onEvent(event);
            return;
        }
        if (event instanceof ProviderEvent.TranscriptDone transcript) {
            listener.onEvent(event);
            if (!transcript.text().isBlank() && chatStream == null) {
                startChat(transcript.text());
            }
            return;
        }
        if (event instanceof ProviderEvent.ProviderError) {
            listener.onEvent(event);
        }
    }

    private void startChat(String transcript) {
        List<ChatRequest.ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new ChatRequest.ChatMessage("system", systemPrompt));
        }
        messages.add(new ChatRequest.ChatMessage("user", transcript));
        chatStream = chatClient.stream(new ChatRequest(chatModel, messages), this::onChatEvent);
    }

    private synchronized void onChatEvent(ProviderEvent event) {
        if (cancelled || closed) {
            return;
        }
        if (event instanceof ProviderEvent.TextDelta text) {
            listener.onEvent(event);
            appendSpeakableText(text.text());
            return;
        }
        if (event instanceof ProviderEvent.TextDone) {
            if (!textDoneForwarded) {
                textDoneForwarded = true;
                listener.onEvent(event);
            }
            flushRemainder();
            chatDone = true;
            maybeFinishAudio();
            return;
        }
        if (event instanceof ProviderEvent.ToolCallDelta
                || event instanceof ProviderEvent.ToolCall
                || event instanceof ProviderEvent.Usage
                || event instanceof ProviderEvent.ProviderError) {
            listener.onEvent(event);
        }
    }

    private void appendSpeakableText(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        textBuffer.append(delta);
        int boundary;
        while ((boundary = firstSentenceBoundary(textBuffer)) >= 0) {
            enqueueTts(textBuffer.substring(0, boundary + 1));
            textBuffer.delete(0, boundary + 1);
        }
        if (textBuffer.length() >= MAX_UNSPOKEN_CHARS) {
            enqueueTts(textBuffer.substring(0, MAX_UNSPOKEN_CHARS));
            textBuffer.delete(0, MAX_UNSPOKEN_CHARS);
        }
        startNextTtsIfIdle();
    }

    private void flushRemainder() {
        String value = textBuffer.toString().trim();
        textBuffer.setLength(0);
        if (!value.isEmpty()) {
            enqueueTts(value);
            startNextTtsIfIdle();
        }
    }

    private void enqueueTts(String text) {
        if (ttsQueue.size() >= MAX_TTS_QUEUE) {
            listener.onEvent(new ProviderEvent.ProviderError(
                    "cascade_tts_queue_full", "Cascade TTS queue is full", false));
            cancelCurrentResponse();
            return;
        }
        ttsQueue.addLast(text);
    }

    private void startNextTtsIfIdle() {
        if (cancelled || closed || ttsStream != null || ttsQueue.isEmpty()) {
            return;
        }
        String text = ttsQueue.removeFirst();
        final TtsStream[] holder = new TtsStream[1];
        TtsStream stream = ttsClient.stream(new TtsRequest(ttsModel, text), event -> onTtsEvent(holder[0], event));
        holder[0] = stream;
        ttsStream = stream;
    }

    private synchronized void onTtsEvent(TtsStream source, ProviderEvent event) {
        if (cancelled || closed || source == null || source != ttsStream) {
            return;
        }
        if (event instanceof ProviderEvent.AudioDelta
                || event instanceof ProviderEvent.ProviderError
                || event instanceof ProviderEvent.Usage) {
            listener.onEvent(event);
            return;
        }
        if (event instanceof ProviderEvent.AudioDone) {
            ttsStream = null;
            startNextTtsIfIdle();
            maybeFinishAudio();
        }
    }

    private void maybeFinishAudio() {
        if (chatDone && ttsStream == null && ttsQueue.isEmpty() && textBuffer.isEmpty() && !audioDoneEmitted) {
            audioDoneEmitted = true;
            listener.onEvent(new ProviderEvent.AudioDone());
        }
    }

    private void resetTurn() {
        cancelled = false;
        chatDone = false;
        audioDoneEmitted = false;
        textDoneForwarded = false;
        textBuffer.setLength(0);
        ttsQueue.clear();
        asrSession = null;
        chatStream = null;
        ttsStream = null;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Cascade pipeline is closed");
        }
    }

    private static int firstSentenceBoundary(CharSequence value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == ';'
                    || c == '；' || c == '\n') {
                return i;
            }
        }
        return -1;
    }
}
