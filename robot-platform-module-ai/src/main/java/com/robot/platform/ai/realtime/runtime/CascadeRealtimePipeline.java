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
import com.robot.platform.ai.model.client.TtsSession;
import java.util.concurrent.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class CascadeRealtimePipeline implements RealtimeProviderSession {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CascadeRealtimePipeline.class);
    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread t = new Thread(task, "cascade-text-flush"); t.setDaemon(true); return t;
    });
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
    private final Function<String, String> memoryContextProvider;

    private final Deque<Delivery> deliveries = new ArrayDeque<>();
    private boolean delivering;
    private record Delivery(TurnStamp turn, ProviderEvent event) { }

    private final StringBuilder textBuffer = new StringBuilder();
    private final Deque<String> ttsQueue = new ArrayDeque<>();
    private final Deque<ChatRequest.ChatMessage> history = new ArrayDeque<>();
    private String turnUserText;
    private String turnAssistantText;
    private final StringBuilder turnAssistantDeltas = new StringBuilder();
    private boolean historySaved;

    private AsrSession asrSession;
    private ChatStream chatStream;
    private TtsStream ttsStream;
    private TtsSession ttsInput;
    private boolean memorySuppressed;
    private ProviderEvent.ProviderError ttsWarmError;
    private long ttsAttempt;
    private int ttsRetries, replayChars;
    private final List<String> ttsReplay = new ArrayList<>();
    private boolean forgetTurn;
    private boolean ttsInputDone, ttsInputFinished, chatStarting, firstText, firstAudio, firstChunk;
    private long epoch, submittedNanos;
    private ScheduledFuture<?> flushTask;
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
                Objects.requireNonNull(listener, "listener"), null, text -> "");
    }

    public CascadeRealtimePipeline(ResolvedModel asrModel,
                                   ResolvedModel chatModel,
                                   ResolvedModel ttsModel,
                                   String systemPrompt,
                                   ModelClientRegistry registry,
                                   RealtimeTurnListener listener) {
        this(asrModel, chatModel, ttsModel, systemPrompt, registry,
                null, Objects.requireNonNull(listener, "listener"), text -> "");
    }

    public CascadeRealtimePipeline(ResolvedModel asrModel, ResolvedModel chatModel, ResolvedModel ttsModel,
                                   String systemPrompt, ModelClientRegistry registry,
                                   RealtimeTurnListener listener, Function<String, String> memoryContextProvider) {
        this(asrModel, chatModel, ttsModel, systemPrompt, registry, null,
                Objects.requireNonNull(listener, "listener"), memoryContextProvider);
    }

    private CascadeRealtimePipeline(ResolvedModel asrModel,
                                    ResolvedModel chatModel,
                                    ResolvedModel ttsModel,
                                    String systemPrompt,
                                    ModelClientRegistry registry,
                                    RealtimeProviderListener legacyListener,
                                    RealtimeTurnListener turnListener,
                                    Function<String, String> memoryContextProvider) {
        this.asrModel = Objects.requireNonNull(asrModel, "asrModel");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.ttsModel = Objects.requireNonNull(ttsModel, "ttsModel");
        this.systemPrompt = systemPrompt;
        Objects.requireNonNull(registry, "registry");
        this.legacyListener = legacyListener;
        this.turnListener = turnListener;
        this.memoryContextProvider = memoryContextProvider == null ? text -> "" : memoryContextProvider;
        this.asrClient = registry.requireAsr(asrModel.providerType());
        this.chatClient = registry.requireChat(chatModel.providerType());
        this.ttsClient = registry.requireTts(ttsModel.providerType());
    }

    @Override
    public void beginTurn(String turnId, long generation) {
        try {
            synchronized (this) {
                if (turnId == null || turnId.isBlank() || generation <= 0) {
                    throw new IllegalArgumentException("Valid turnId and generation are required");
                }
                currentTurn = new TurnStamp(turnId, generation);

            }
        } finally {
            dispatchEvents();
        }
    }

    @Override
    public void speechStarted() {
        try {
            synchronized (this) {
                requireOpen();
                if (speaking) {
                    throw new IllegalStateException("Cascade user speech already started");
                }
                resetTurn();
                TurnStamp turn = turnListener == null ? null : requireCurrentTurn();
                speaking = true;
                asrSession = asrClient.open(asrModel, event -> onAsrEvent(turn, event));
                asrSession.speechStarted();
                long version = epoch;
                openIncrementalTts(turn, version);

            }
        } finally {
            dispatchEvents();
        }
    }

    @Override
    public void appendAudio(ByteBuffer pcm) {
        try {
            synchronized (this) {
                requireOpen();
                if (!speaking || asrSession == null) {
                    throw new IllegalStateException("Cascade audio requires active user speech");
                }
                asrSession.appendAudio(pcm);

            }
        } finally {
            dispatchEvents();
        }
    }

    @Override
    public void speechStopped() {
        try {
            synchronized (this) {
                requireOpen();
                if (!speaking || asrSession == null) {
                    throw new IllegalStateException("Cascade user speech has not started");
                }
                speaking = false;
                submittedNanos = System.nanoTime();
                asrSession.speechStopped();

            }
        } finally {
            dispatchEvents();
        }
    }

    @Override
    public void cancelCurrentResponse() {
        try {
            synchronized (this) {
                if (closed) {
                    return;
                }
                cancelled = true;
                epoch++;
                if (flushTask != null) flushTask.cancel(false);
                if (ttsInput != null) ttsInput.cancel();
                speaking = false;
                textBuffer.setLength(0);
                ttsQueue.clear();
                ttsReplay.clear(); replayChars = 0;
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
        } finally {
            dispatchEvents();
        }
    }

    @Override
    public void close() {
        try {
            synchronized (this) {
                if (closed) {
                    return;
                }
                cancelCurrentResponse();
                closed = true;

            }
        } finally {
            dispatchEvents();
        }
    }

    private void onAsrEvent(TurnStamp turn, ProviderEvent event) {
        String transcript = null;
        long version = 0;
        synchronized (this) {
            if (cancelled || closed || !matchesCurrent(turn)) return;
            emit(turn, event);
            if (event instanceof ProviderEvent.TranscriptDone done && !ConversationExitIntent.matches(done.text())
                    && done.text() != null && !done.text().replaceAll("[\\p{P}\\p{Z}\\s]", "").isBlank()
                    && chatStream == null && !chatStarting) {
                chatStarting = true; transcript = done.text(); version = epoch;
                timing("asr_done");
            } else if (event instanceof ProviderEvent.TranscriptDone) {
                if (ttsInput != null) ttsInput.cancel();
                ttsInput = null;
            }
        }
        // Final transcription reaches the device immediately; network/recall never holds the state lock.
        dispatchEvents();
        if (transcript != null) startChat(turn, transcript, version);
    }

    private void startChat(TurnStamp turn, String transcript, long version) {
        try {
            synchronized (this) {
                if (cancelled || closed || epoch != version) return;
                if (ttsWarmError != null) handleTtsFailure(turn, version, ttsWarmError);
                if (cancelled || closed || epoch != version) return;
            }
            var directive = new com.robot.platform.ai.memory.policy.MemoryDirectiveParser().parse(transcript);
            boolean forgetting = directive == com.robot.platform.ai.memory.policy.MemoryDirectiveParser.Directive.FORGET;
            synchronized (this) {
                if (epoch != version || cancelled || closed) return;
                if (forgetting) { history.clear(); memorySuppressed = true; }
            }
            String memoryContext = memorySuppressed ? "" : memoryContextProvider.apply(transcript);
            synchronized (this) {
                if (cancelled || closed || epoch != version || !matchesCurrent(turn)) return;
                timing("memory_done");
                List<ChatRequest.ChatMessage> messages = new ArrayList<>();
                String instructions = systemPrompt == null ? "" : systemPrompt;
                if (memoryContext != null && !memoryContext.isBlank()) instructions += "\n\n" + memoryContext;
                var context = ConversationContextPolicy.select(transcript, List.copyOf(history));
                instructions += "\n\n<current_turn_context>\n" + context.instructions() + "\n</current_turn_context>";
                if (!instructions.isBlank()) messages.add(new ChatRequest.ChatMessage("system", instructions));
                forgetTurn = forgetting;
                messages.addAll(context.messages());
                LOG.info("Cascade context: turn={} mode={} retainedTurns={} availableTurns={}",
                        turn, context.mode(), context.messages().size() / 2, history.size() / 2);
                turnUserText = transcript;
                messages.add(new ChatRequest.ChatMessage("user", transcript));
                chatStream = chatClient.stream(new ChatRequest(chatModel, messages), event -> onChatEvent(turn, event));
            }
        } catch (RuntimeException error) {
            synchronized (this) {
                if (epoch == version && !cancelled && !closed) {
                    emit(turn, new ProviderEvent.ProviderError("cascade_start_failed", "Unable to start voice response", true));
                    cancelCurrentResponse();
                }
            }
        } finally { dispatchEvents(); }
    }

    private void openIncrementalTts(TurnStamp turn, long version) {
        long attempt = ++ttsAttempt;
        ttsInput = null;
        ttsWarmError = null;
        ttsInputFinished = false;
        TtsSession opened = null;
        try {
            opened = ttsClient.openSession(ttsModel, event -> onIncrementalTts(turn, version, attempt, event));
            if (cancelled || closed || epoch != version || attempt != ttsAttempt || ttsWarmError != null) {
                if (opened != null) opened.cancel();
                return;
            }
            ttsInput = opened;
            if (opened != null) {
                for (String text : List.copyOf(ttsReplay)) {
                    if (cancelled || attempt != ttsAttempt) return;
                    opened.appendText(text);
                }
                if (attempt == ttsAttempt && !cancelled) finishTtsInput();
            }
        } catch (RuntimeException unavailable) {
            if (opened != null) opened.cancel();
            if (attempt == ttsAttempt && epoch == version && !cancelled)
                handleTtsFailure(turn, version, new ProviderEvent.ProviderError("tts_open_failed", "Unable to prepare voice output", false));
        }
    }

    private void handleTtsFailure(TurnStamp turn, long version, ProviderEvent.ProviderError error) {
        if (ttsInput != null) ttsInput.cancel();
        ttsInput = null;
        if (!chatStarting) { ttsWarmError = error; return; }
        if (!firstAudio && error.retryable() && ttsRetries < 1) {
            ttsRetries++;
            LOG.warn("Retrying TTS before first audio: turn={} code={} attempt=2", turn, error.code());
            openIncrementalTts(turn, version);
        } else {
            emit(turn, error);
            cancelCurrentResponse();
        }
    }

    private void onIncrementalTts(TurnStamp turn, long version, long attempt, ProviderEvent event) {
        try {
            synchronized (this) {
                if (cancelled || closed || epoch != version || attempt != ttsAttempt || !matchesCurrent(turn)) return;
                if (event instanceof ProviderEvent.ProviderError error) {
                    handleTtsFailure(turn, version, error); return;
                }
                if (event instanceof ProviderEvent.AudioDone) { ttsInputDone = true; maybeFinishAudio(); }
                else {
                    if (event instanceof ProviderEvent.AudioDelta && !firstAudio) {
                        firstAudio = true; ttsReplay.clear(); replayChars = 0; timing("first_audio");
                    }
                    emit(turn, event);
                }
            }
        } finally { dispatchEvents(); }
    }

    private void timing(String stage) {
        LOG.info("Cascade timing: turn={} stage={} sinceSubmitMs={}", currentTurn, stage,
                submittedNanos == 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - submittedNanos));
    }

    private void onChatEvent(TurnStamp turn, ProviderEvent event) {
        try {
            synchronized (this) {
                if (cancelled || closed || !matchesCurrent(turn)) {
                    return;
                }
                if (event instanceof ProviderEvent.TextDelta text) {
                    if (!firstText) { firstText = true; timing("first_text"); }
                    turnAssistantDeltas.append(text.text());
                    emit(turn, event);
                    appendSpeakableText(text.text());
                    return;
                }
                if (event instanceof ProviderEvent.TextDone done) {
                    turnAssistantText = done.text() == null || done.text().isBlank() ? turnAssistantDeltas.toString() : done.text();
                    if (!textDoneForwarded) {
                        textDoneForwarded = true;
                        emit(turn, new ProviderEvent.TextDone(turnAssistantText));
                    }
                    flushRemainder();
                    chatDone = true;
                    finishTtsInput();
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
        } finally {
            dispatchEvents();
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
        int limit = firstChunk ? MAX_UNSPOKEN_CHARS : 24;
        while (textBuffer.length() >= limit) {
            int end = safeChunkEnd(textBuffer, limit);
            if (end == 0) break;
            enqueueTts(textBuffer.substring(0, end)); textBuffer.delete(0, end);
            limit = MAX_UNSPOKEN_CHARS;
        }
        startNextTtsIfIdle();
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (textBuffer.isEmpty() || flushTask != null && !flushTask.isDone()) return;
        long version = epoch;
        flushTask = FLUSHER.schedule(() -> {
            try {
                synchronized (this) {
                    if (version != epoch || cancelled || closed || chatDone) return;
                    flushTask = null;
                    // A timer may flush Chinese or a complete word, never half an English word/number.
                    int end = safeChunkEnd(textBuffer, Math.min(textBuffer.length(), firstChunk ? 48 : 24));
                    if (end >= 8) {
                        enqueueTts(textBuffer.substring(0, end)); textBuffer.delete(0, end); startNextTtsIfIdle();
                    }
                    scheduleFlush();
                }
            } finally { dispatchEvents(); }
        }, firstChunk ? 400 : 250, TimeUnit.MILLISECONDS);
    }

    private static int safeChunkEnd(CharSequence text, int maximum) {
        for (int i = maximum; i > 0; i--) {
            char c = text.charAt(i - 1);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN || Character.isWhitespace(c)
                    || "，,。！？；;!?".indexOf(c) >= 0) return i;
        }
        return 0;
    }

    private void finishTtsInput() {
        if (cancelled || closed) return;
        if (ttsInput != null && chatDone && !ttsInputFinished) { ttsInputFinished = true; ttsInput.finishInput(); }
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
        // LLM formatting newlines or punctuation alone have nothing to synthesize.
        if (text == null || text.codePoints().noneMatch(Character::isLetterOrDigit)) return;
        text = text.trim();
        if (ttsQueue.size() >= MAX_TTS_QUEUE) {
            emit(currentTurn, new ProviderEvent.ProviderError(
                    "cascade_tts_queue_full", "Cascade TTS queue is full", false));
            cancelCurrentResponse();
            return;
        }
        if (!firstChunk) { firstChunk = true; timing("first_tts_text"); }
        if (ttsInput != null) {
            if (!firstAudio) {
                if (replayChars + text.length() > 8192) {
                    emit(currentTurn, new ProviderEvent.ProviderError("tts_input_full", "TTS input queue is full", false));
                    cancelCurrentResponse(); return;
                }
                ttsReplay.add(text); replayChars += text.length();
            }
            try { ttsInput.appendText(text); }
            catch (RuntimeException error) {
                handleTtsFailure(currentTurn, epoch,
                        new ProviderEvent.ProviderError("tts_input_failed", "TTS input rejected", true));
            }
        } else ttsQueue.addLast(text);
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

    private void onTtsEvent(TurnStamp turn, TtsStream source, ProviderEvent event) {
        try {
            synchronized (this) {
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
        } finally {
            dispatchEvents();
        }
    }

    private void maybeFinishAudio() {
        if (cancelled || closed) return;
        if (chatDone && (ttsInput == null || ttsInputDone) && ttsStream == null && ttsQueue.isEmpty() && textBuffer.isEmpty() && !audioDoneEmitted) {
            audioDoneEmitted = true;
            if (!forgetTurn && !historySaved && turnUserText != null && turnAssistantText != null && !turnAssistantText.isBlank()) {
                historySaved = true;
                history.addLast(new ChatRequest.ChatMessage("user", turnUserText));
                history.addLast(new ChatRequest.ChatMessage("assistant", turnAssistantText));
                while (history.size() > 8 || history.stream().mapToInt(m -> m.content().length()).sum() > 4000) {
                    if (history.size() <= 2) break;
                    history.removeFirst(); history.removeFirst();
                }
            }
            emit(currentTurn, new ProviderEvent.AudioDone());
        }
    }

    private void resetTurn() {
        epoch++;
        if (flushTask != null) flushTask.cancel(false);
        flushTask = null;
        if (ttsInput != null) ttsInput.cancel();
        ttsInput = null;
        ttsWarmError = null;
        ttsRetries = replayChars = 0;
        ttsReplay.clear();
        forgetTurn = false;
        ttsInputDone = ttsInputFinished = chatStarting = firstText = firstAudio = firstChunk = false;
        submittedNanos = 0;
        cancelled = false;
        chatDone = false;
        audioDoneEmitted = false;
        textDoneForwarded = false;
        historySaved = false;
        turnUserText = turnAssistantText = null;
        turnAssistantDeltas.setLength(0);
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
        deliveries.addLast(new Delivery(turn == null ? currentTurn : turn, event));
    }

    private void dispatchEvents() {
        if (Thread.holdsLock(this)) return;
        synchronized (this) { if (delivering) return; delivering = true; }
        boolean released = false;
        try {
            while (true) {
                Delivery next;
                synchronized (this) {
                    next=deliveries.pollFirst();
                    if (next==null) { delivering=false; released=true; return; }
                }
                if (turnListener != null) {
                    var turn=next.turn();
                    turnListener.onEvent(turn==null?null:turn.turnId(),turn==null?0:turn.generation(),next.event());
                } else legacyListener.onEvent(next.event());
            }
        } finally { if (!released) synchronized(this) { delivering=false; } }
    }

    private record TurnStamp(String turnId, long generation) {
    }

    private static int firstSentenceBoundary(CharSequence value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((i >= 8 && (c == '，' || c == ',')) || c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == ';'
                    || c == '；' || c == '\n') {
                return i;
            }
        }
        return -1;
    }
}
