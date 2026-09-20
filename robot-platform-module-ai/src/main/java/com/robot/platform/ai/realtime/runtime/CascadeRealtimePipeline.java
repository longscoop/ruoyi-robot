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
import com.robot.platform.ai.model.client.RealtimeTurnListener;
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
    private final RealtimeProviderListener legacyListener;
    private final RealtimeTurnListener turnListener;

    private final StringBuilder textBuffer = new StringBuilder();
    private final Deque<String> ttsQueue = new ArrayDeque<>();

    private AsrSession asrSession;
    private ChatStream chatStream;
    private TtsStream ttsStream;
    private TurnStamp currentTurn;
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
        this(asrModel, chatModel, ttsModel, systemPrompt, registry,
                Objects.requireNonNull(listener, "listener"), null);
    }

    public CascadeRealtimePipeline(ResolvedModel asrModel,
                                   ResolvedModel chatModel,
                                   ResolvedModel ttsModel,
                                   String systemPrompt,
                                   ModelClientRegistry registry,
                                   RealtimeTurnListener listener) {
        this(asrModel, chatModel, ttsModel, systemPrompt, registry,
                null, Objects.requireNonNull(listener, "listener"));
    }

    private CascadeRealtimePipeline(ResolvedModel asrModel,
                                    ResolvedModel chatModel,
                                    ResolvedModel ttsModel,
                                    String systemPrompt,
                                    ModelClientRegistry registry,
                                    RealtimeProviderListener legacyListener,
                                    RealtimeTurnListener turnListener) {
        this.asrModel = Objects.requireNonNull(asrModel, "asrModel");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.ttsModel = Objects.requireNonNull(ttsModel, "ttsModel");
        this.systemPrompt = systemPrompt;
        Objects.requireNonNull(registry, "registry");
        this.legacyListener = legacyListener;
        this.turnListener = turnListener;
        this.asrClient = registry.requireAsr(asrModel.providerType());
        this.chatClient = registry.requireChat(chatModel.providerType());
        this.ttsClient = registry.requireTts(ttsModel.providerType());
    }

    @Override
    public synchronized void beginTurn(String turnId, long generation) {
        if (turnId == null || turnId.isBlank() || generation <= 0) {
            throw new IllegalArgumentException("Valid turnId and generation are required");
        }
        currentTurn = new TurnStamp(turnId, generation);
    }

    @Override
    public synchronized void speechStarted() {
        requireOpen();
        if (speaking) {
            throw new IllegalStateException("Cascade user speech already started");
        }
        resetTurn();
        TurnStamp turn = turnListener == null ? null : requireCurrentTurn();
        speaking = true;
        asrSession = asrClient.open(asrModel, event -> onAsrEvent(turn, event));
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

    private synchronized void onAsrEvent(TurnStamp turn, ProviderEvent event) {
        if (cancelled || closed || !matchesCurrent(turn)) {
            return;
        }
        if (event instanceof ProviderEvent.TranscriptDelta) {
            emit(turn, event);
            return;
        }
        if (event instanceof ProviderEvent.TranscriptDone transcript) {
            emit(turn, event);
            if (!transcript.text().isBlank() && chatStream == null) {
                startChat(turn, transcript.text());
            }
            return;
        }
        if (event instanceof ProviderEvent.ProviderError) {
            emit(turn, event);
        }
    }

    private void startChat(TurnStamp turn, String transcript) {
        List<ChatRequest.ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new ChatRequest.ChatMessage("system", systemPrompt));
        }
        messages.add(new ChatRequest.ChatMessage("user", transcript));
        chatStream = chatClient.stream(new ChatRequest(chatModel, messages), event -> onChatEvent(turn, event));
    }

    private synchronized void onChatEvent(TurnStamp turn, ProviderEvent event) {
        if (cancelled || closed || !matchesCurrent(turn)) {
            return;
        }
        if (event instanceof ProviderEvent.TextDelta text) {
            emit(turn, event);
            appendSpeakableText(text.text());
            return;
        }
        if (event instanceof ProviderEvent.TextDone) {
            if (!textDoneForwarded) {
                textDoneForwarded = true;
                emit(turn, event);
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
            emit(turn, event);
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
            emit(currentTurn, new ProviderEvent.ProviderError(
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
        TurnStamp turn = currentTurn;
        TtsStream stream = ttsClient.stream(new TtsRequest(ttsModel, text),
                event -> onTtsEvent(turn, holder[0], event));
        holder[0] = stream;
        ttsStream = stream;
    }

    private synchronized void onTtsEvent(TurnStamp turn, TtsStream source, ProviderEvent event) {
        if (cancelled || closed || !matchesCurrent(turn) || source == null || source != ttsStream) {
            return;
        }
        if (event instanceof ProviderEvent.AudioDelta
                || event instanceof ProviderEvent.ProviderError
                || event instanceof ProviderEvent.Usage) {
            emit(turn, event);
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
            emit(currentTurn, new ProviderEvent.AudioDone());
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

    private boolean matchesCurrent(TurnStamp turn) {
        return turnListener == null || (turn != null && turn.equals(currentTurn));
    }

    private TurnStamp requireCurrentTurn() {
        if (currentTurn == null) {
            throw new IllegalStateException("Cascade turn context is not initialized");
        }
        return currentTurn;
    }

    private void emit(TurnStamp turn, ProviderEvent event) {
        if (turnListener != null) {
            TurnStamp actual = turn == null ? currentTurn : turn;
            if (actual != null) {
                turnListener.onEvent(actual.turnId(), actual.generation(), event);
            } else {
                turnListener.onEvent(null, 0L, event);
            }
        } else {
            legacyListener.onEvent(event);
        }
    }

    private record TurnStamp(String turnId, long generation) {
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
