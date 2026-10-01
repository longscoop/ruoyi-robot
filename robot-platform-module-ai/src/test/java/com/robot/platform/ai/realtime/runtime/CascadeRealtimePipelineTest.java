package com.robot.platform.ai.realtime.runtime;

import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CascadeRealtimePipelineTest {

    @Test
    void chatStartsOnlyAfterFinalAsrAndTtsPreservesSpeakableChunkOrder() {
        FakeAsrClient asr=new FakeAsrClient();
        FakeChatClient chat=new FakeChatClient();
        FakeTtsClient tts=new FakeTtsClient();
        ModelClientRegistry registry=new ModelClientRegistry(List.of(),List.of(chat),List.of(asr),List.of(tts));
        List<ProviderEvent> output=new ArrayList<>();
        CascadeRealtimePipeline pipeline=new CascadeRealtimePipeline(
                asrModel(),chatModel(),ttsModel(),"你是小优。",registry,output::add);

        pipeline.speechStarted();
        pipeline.appendAudio(ByteBuffer.wrap(new byte[]{1,2}));
        pipeline.speechStopped();

        assertEquals(0,chat.requests.size());
        asr.emit(new ProviderEvent.TranscriptDelta("你"));
        assertEquals(0,chat.requests.size());

        asr.emit(new ProviderEvent.TranscriptDone("你好"));
        assertEquals(1,chat.requests.size());
        assertTrue(chat.requests.get(0).messages().get(0).content().startsWith("你是小优。"));
        assertEquals("你好",chat.requests.get(0).messages().get(1).content());

        chat.emit(new ProviderEvent.TextDelta("第一句。"));
        assertEquals(List.of("第一句。"),tts.texts);
        chat.emit(new ProviderEvent.TextDelta("第二句。"));
        assertEquals(List.of("第一句。"),tts.texts);

        chat.emit(new ProviderEvent.TextDone("stop"));
        tts.emit(0,new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{7})));
        tts.emit(0,new ProviderEvent.AudioDone());
        assertEquals(List.of("第一句。","第二句。"),tts.texts);
        tts.emit(1,new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{8})));
        tts.emit(1,new ProviderEvent.AudioDone());

        List<byte[]> audios=output.stream().filter(ProviderEvent.AudioDelta.class::isInstance)
                .map(ProviderEvent.AudioDelta.class::cast).map(e->{ByteBuffer b=e.audio();byte[] v=new byte[b.remaining()];b.get(v);return v;}).toList();
        assertArrayEquals(new byte[]{7},audios.get(0));
        assertArrayEquals(new byte[]{8},audios.get(1));
        assertEquals(1,output.stream().filter(ProviderEvent.AudioDone.class::isInstance).count());
    }

    @Test
    void cancellationStopsEveryStageClearsQueueAndDropsLateAudio() {
        FakeAsrClient asr=new FakeAsrClient();
        FakeChatClient chat=new FakeChatClient();
        FakeTtsClient tts=new FakeTtsClient();
        ModelClientRegistry registry=new ModelClientRegistry(List.of(),List.of(chat),List.of(asr),List.of(tts));
        List<ProviderEvent> output=new ArrayList<>();
        CascadeRealtimePipeline pipeline=new CascadeRealtimePipeline(
                asrModel(),chatModel(),ttsModel(),null,registry,output::add);

        pipeline.speechStarted();
        asr.emit(new ProviderEvent.TranscriptDone("你好"));
        chat.emit(new ProviderEvent.TextDelta("第一句。"));
        chat.emit(new ProviderEvent.TextDelta("第二句。"));
        pipeline.cancelCurrentResponse();

        assertEquals(1,asr.cancelCount);
        assertEquals(1,chat.cancelCount);
        assertEquals(1,tts.cancelCounts.get(0).intValue());

        int before=output.size();
        tts.emit(0,new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{9})));
        tts.emit(0,new ProviderEvent.AudioDone());
        assertEquals(before,output.size());
        assertEquals(List.of("第一句。"),tts.texts);
    }


    @Test void eachQuestionRetrievesMemoryBeforeChatAndIncludesOnlyCompletedHistory() {
        FakeAsrClient asr=new FakeAsrClient(); FakeChatClient chat=new FakeChatClient(); FakeTtsClient tts=new FakeTtsClient();
        var registry=new ModelClientRegistry(List.of(),List.of(chat),List.of(asr),List.of(tts));
        List<String> recalled=new ArrayList<>();
        var pipeline=new CascadeRealtimePipeline(asrModel(),chatModel(),ttsModel(),"角色规则",registry,
                (turn,generation,event)->{},question->{assertEquals(recalled.size(),chat.requests.size());recalled.add(question);return "相关记忆:"+question;});
        pipeline.beginTurn("one",1); pipeline.speechStarted(); pipeline.speechStopped();
        asr.emit(new ProviderEvent.TranscriptDone("我喜欢什么饮料"));
        assertEquals(List.of("我喜欢什么饮料"),recalled);
        assertTrue(chat.requests.get(0).messages().get(0).content().startsWith("角色规则\n\n相关记忆:我喜欢什么饮料"));
        chat.emit(new ProviderEvent.TextDelta("少糖咖啡。"));chat.emit(new ProviderEvent.TextDone("少糖咖啡。"));tts.emit(0,new ProviderEvent.AudioDone());
        pipeline.beginTurn("two",2);pipeline.speechStarted();pipeline.speechStopped();asr.emit(new ProviderEvent.TranscriptDone("那我做什么工作"));
        var messages=chat.requests.get(1).messages();
        assertEquals(List.of("system","user","assistant","user"),messages.stream().map(ChatRequest.ChatMessage::role).toList());
        assertTrue(messages.get(0).content().startsWith("角色规则\n\n相关记忆:那我做什么工作"));
        assertEquals("少糖咖啡。",messages.get(2).content());
        assertEquals("那我做什么工作",messages.get(3).content());
        chat.emit(new ProviderEvent.TextDelta("被打断的猜测。"));pipeline.cancelCurrentResponse();
        pipeline.beginTurn("three",3);pipeline.speechStarted();pipeline.speechStopped();asr.emit(new ProviderEvent.TranscriptDone("重新问一个问题"));
        assertFalse(chat.requests.get(2).messages().stream().anyMatch(m->m.content().contains("被打断")));
    }

    @Test void exitDoesNotStartMemoryLookupChatOrTts() {
        var asr=new FakeAsrClient();var chat=new FakeChatClient();var tts=new FakeTtsClient();
        var registry=new ModelClientRegistry(List.of(),List.of(chat),List.of(asr),List.of(tts));
        var output=new ArrayList<ProviderEvent>();
        var pipeline=new CascadeRealtimePipeline(asrModel(),chatModel(),ttsModel(),"角色",registry,
                (turn,generation,event)->output.add(event),question->{fail("Exit must not query memory");return "";});
        pipeline.beginTurn("exit",1);pipeline.speechStarted();pipeline.speechStopped();
        asr.emit(new ProviderEvent.TranscriptDone("你退出吧。"));
        assertEquals(1,output.size());assertTrue(chat.requests.isEmpty());assertTrue(tts.texts.isEmpty());
    }

    @Test void formattingNewlinesDoNotCreateSilentTtsStreamsOrBlockCompletion() {
        var asr=new FakeAsrClient();var chat=new FakeChatClient();var tts=new FakeTtsClient();
        var registry=new ModelClientRegistry(List.of(),List.of(chat),List.of(asr),List.of(tts));var output=new ArrayList<ProviderEvent>();
        var pipeline=new CascadeRealtimePipeline(asrModel(),chatModel(),ttsModel(),"角色",registry,output::add);
        pipeline.speechStarted();pipeline.speechStopped();asr.emit(new ProviderEvent.TranscriptDone("说的这是啥？"));
        chat.emit(new ProviderEvent.TextDelta("抱歉。  \n～😊\n是旅行建议。！\n"));
        chat.emit(new ProviderEvent.TextDone("抱歉。\n是旅行建议。"));
        tts.emit(0,new ProviderEvent.AudioDone());tts.emit(1,new ProviderEvent.AudioDone());
        assertEquals(List.of("抱歉。","是旅行建议。"),tts.texts);
        assertEquals(1,output.stream().filter(ProviderEvent.AudioDone.class::isInstance).count());
    }

    @Test void incrementalTtsIsPreparedBeforeRecallAndOneSessionReceivesEverySegment() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var registry = new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts));
        var output = new ArrayList<ProviderEvent>();
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色", registry,
                (turn, generation, event) -> output.add(event), question -> {
                    assertEquals(1, tts.opens); return "";
                });
        pipeline.beginTurn("one", 1); pipeline.speechStarted(); pipeline.speechStopped();
        asr.emit(new ProviderEvent.TranscriptDone("你好"));
        chat.emit(new ProviderEvent.TextDelta("第一句。第二句。"));
        assertEquals(List.of("第一句。", "第二句。"), tts.texts);
        chat.emit(new ProviderEvent.TextDone("第一句。第二句。"));
        assertEquals(1, tts.finished);
        assertFalse(output.stream().anyMatch(ProviderEvent.AudioDone.class::isInstance));
        tts.listener.onEvent(new ProviderEvent.AudioDone());
        assertEquals(1, output.stream().filter(ProviderEvent.AudioDone.class::isInstance).count());
        assertEquals(1, tts.opens);
        pipeline.close();
    }

    @Test void recallDoesNotBlockCancellationAndCannotStartChatAfterCancellation() throws Exception {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var registry = new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts));
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色", registry,
                (turn, generation, event) -> {}, question -> {
                    entered.countDown(); try { release.await(2, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); } return "";
                });
        pipeline.beginTurn("one", 1); pipeline.speechStarted(); pipeline.speechStopped();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var pending = executor.submit(() -> asr.emit(new ProviderEvent.TranscriptDone("你好")));
        try {
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofMillis(200), pipeline::cancelCurrentResponse);
            assertEquals(1, tts.cancelled);
            release.countDown(); pending.get(1, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(chat.requests.isEmpty());
        } finally { release.countDown(); executor.shutdownNow(); pipeline.close(); }
    }

    @Test void shortUnpunctuatedTextFlushesOnTimerAndLateTimerDoesNotSpeakAfterCancel() throws Exception {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var registry = new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts));
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色", registry, event -> {});
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("问候"));
        chat.emit(new ProviderEvent.TextDelta("欢迎来到今天的家庭小课堂"));
        assertTrue(tts.submitted.await(1, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(List.of("欢迎来到今天的家庭小课堂"), tts.texts);
        pipeline.cancelCurrentResponse();
        chat.emit(new ProviderEvent.TextDelta("迟到内容不能发出声音"));
        assertEquals(1, tts.texts.size());
        pipeline.close();
    }

    @Test void independentQuestionOmitsOldTopicAndForgetSuppressesRecallForSession() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var recalled = new ArrayList<String>();
        var registry = new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts));
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色", registry,
                (turn, generation, event) -> {}, question -> { recalled.add(question); return ""; });
        for (int n = 1; n <= 4; n++) {
            pipeline.beginTurn("t" + n, n); pipeline.speechStarted(); pipeline.speechStopped();
            String question = List.of("我的猫叫什么", "一加一等于几", "忘记我的猫", "我的猫叫什么").get(n - 1);
            asr.emit(new ProviderEvent.TranscriptDone(question));
            if (n == 2) assertFalse(chat.requests.get(n - 1).messages().stream().anyMatch(m -> m.content().contains("小黑")));
            if (n == 3 || n == 4) assertFalse(chat.requests.get(n - 1).messages().stream().anyMatch(m -> m.content().contains("小黑")));
            String answer = n == 1 ? "小黑。" : "好的。";
            chat.emit(new ProviderEvent.TextDelta(answer)); chat.emit(new ProviderEvent.TextDone(answer));
            tts.listener.onEvent(new ProviderEvent.AudioDone());
        }
        assertEquals(2, recalled.size()); pipeline.close();
    }

    @Test void shortReplyRetainsTheAssistantQuestionAndStory() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var registry = new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts));
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色", registry, event -> {});
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("讲个搞笑故事"));
        String story = "泡面里放了饺子。你猜吃了吗？";
        chat.emit(new ProviderEvent.TextDelta(story)); chat.emit(new ProviderEvent.TextDone(story));
        tts.listener.onEvent(new ProviderEvent.AudioDone());
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("吃了吧。"));
        assertEquals(List.of("system", "user", "assistant", "user"), chat.requests.get(1).messages().stream().map(ChatRequest.ChatMessage::role).toList());
        assertEquals(story, chat.requests.get(1).messages().get(2).content());
        pipeline.close();
    }

    @Test void failureBeforeAudioReplaysCompletedTextOnceAndIgnoresOldConnection() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var output = new ArrayList<ProviderEvent>();
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色",
                new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts)), output::add);
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("你好"));
        chat.emit(new ProviderEvent.TextDelta("第一句。第二句。")); chat.emit(new ProviderEvent.TextDone("第一句。第二句。"));
        TtsListener old = tts.listener;
        old.onEvent(new ProviderEvent.ProviderError("tts_connect_failed", "failed", true));
        assertEquals(2, tts.opens); assertEquals(2, tts.finished);
        assertEquals(List.of("第一句。", "第二句。", "第一句。", "第二句。"), tts.texts);
        old.onEvent(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{9})));
        old.onEvent(new ProviderEvent.AudioDone());
        assertFalse(output.stream().anyMatch(ProviderEvent.AudioDone.class::isInstance));
        tts.listener.onEvent(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{1})));
        tts.listener.onEvent(new ProviderEvent.AudioDone());
        assertEquals(1, output.stream().filter(ProviderEvent.AudioDelta.class::isInstance).count());
        assertEquals(1, output.stream().filter(ProviderEvent.AudioDone.class::isInstance).count());
        assertFalse(output.stream().anyMatch(ProviderEvent.ProviderError.class::isInstance)); pipeline.close();
    }

    @Test void secondFailureEndsOnlyThatTurnAndCancellationDropsRetryCallbacks() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var output = new ArrayList<ProviderEvent>();
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色",
                new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts)), output::add);
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("你好"));
        tts.listener.onEvent(new ProviderEvent.ProviderError("tts_connect_failed", "failed", true));
        TtsListener retry = tts.listener;
        retry.onEvent(new ProviderEvent.ProviderError("tts_connect_failed", "failed", true));
        assertEquals(2, tts.opens);
        assertEquals(1, output.stream().filter(ProviderEvent.ProviderError.class::isInstance).count());
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("再试一次"));
        retry.onEvent(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{9})));
        chat.emit(new ProviderEvent.TextDelta("好的。")); chat.emit(new ProviderEvent.TextDone("好的。"));
        tts.listener.onEvent(new ProviderEvent.AudioDone());
        assertFalse(output.stream().anyMatch(ProviderEvent.AudioDelta.class::isInstance));
        assertEquals(1, output.stream().filter(ProviderEvent.AudioDone.class::isInstance).count()); pipeline.close();
    }

    @Test void alreadyPlayedAudioAndPermanentErrorsAreNotRetried() {
        for (boolean played : List.of(false, true)) {
            var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
            var output = new ArrayList<ProviderEvent>();
            var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色",
                    new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts)), output::add);
            pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("你好"));
            chat.emit(new ProviderEvent.TextDelta("你好。"));
            if (played) tts.listener.onEvent(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{1})));
            tts.listener.onEvent(new ProviderEvent.ProviderError("tts_transport_error", "failed", played));
            assertEquals(1, tts.opens);
            assertEquals(1, output.stream().filter(ProviderEvent.ProviderError.class::isInstance).count()); pipeline.close();
        }
    }

    @Test void warmFailureWaitsForInputAndUsesTheSameSingleRetryBudget() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var output = new ArrayList<ProviderEvent>();
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色",
                new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts)), output::add);
        pipeline.speechStarted();
        tts.listener.onEvent(new ProviderEvent.ProviderError("tts_connect_failed", "failed", true));
        assertEquals(1, tts.opens); assertTrue(output.isEmpty());
        pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("你好"));
        assertEquals(2, tts.opens);
        tts.listener.onEvent(new ProviderEvent.ProviderError("tts_connect_failed", "failed", true));
        assertEquals(2, tts.opens);
        assertEquals(1, output.stream().filter(ProviderEvent.ProviderError.class::isInstance).count()); pipeline.close();
    }

    @Test void retryAcceptsNewTextAndUserInterruptionCancelsIt() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var output = new ArrayList<ProviderEvent>();
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色",
                new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts)), output::add);
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("你好"));
        chat.emit(new ProviderEvent.TextDelta("第一句。"));
        tts.listener.onEvent(new ProviderEvent.ProviderError("tts_connect_failed", "failed", true));
        chat.emit(new ProviderEvent.TextDelta("第二句。"));
        assertEquals(List.of("第一句。", "第一句。", "第二句。"), tts.texts);
        pipeline.cancelCurrentResponse();
        int before = output.size();
        tts.listener.onEvent(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{9})));
        tts.listener.onEvent(new ProviderEvent.AudioDone());
        chat.emit(new ProviderEvent.TextDone("第一句。第二句。"));
        assertEquals(before, output.size()); assertEquals(2, tts.cancelled); pipeline.close();
    }

    @Test void immediateConnectionFailuresStillRespectTheRetryBudget() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        tts.immediateFailures = 2;
        var output = new ArrayList<ProviderEvent>();
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色",
                new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts)), output::add);
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("你好"));
        assertEquals(2, tts.opens); assertTrue(chat.requests.isEmpty());
        assertEquals(1, output.stream().filter(ProviderEvent.ProviderError.class::isInstance).count()); pipeline.close();
    }

    @Test void failureWhileFlushingFinalTextDoesNotCompleteOrSaveUnheardAnswer() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        tts.rejectInput = true;
        var output = new ArrayList<ProviderEvent>();
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色",
                new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts)), output::add);
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("问候"));
        chat.emit(new ProviderEvent.TextDelta("你好呀")); chat.emit(new ProviderEvent.TextDone("你好呀"));
        assertEquals(1, output.stream().filter(ProviderEvent.ProviderError.class::isInstance).count());
        assertFalse(output.stream().anyMatch(ProviderEvent.AudioDone.class::isInstance));
        tts.rejectInput = false;
        pipeline.speechStarted(); pipeline.speechStopped(); asr.emit(new ProviderEvent.TranscriptDone("再问"));
        assertEquals(2, chat.requests.get(1).messages().size()); pipeline.close();
    }

    @Test void screenshotSequenceDoesNotLeakPetIntoMathOrStoryButKeepsStoryFollowUp() {
        var asr = new FakeAsrClient(); var chat = new FakeChatClient(); var tts = new IncrementalTts();
        var pipeline = new CascadeRealtimePipeline(asrModel(), chatModel(), ttsModel(), "角色",
                new ModelClientRegistry(List.of(), List.of(chat), List.of(asr), List.of(tts)),
                (turn, generation, event) -> {}, q -> q.contains("猫") ? "用户家中有一只名叫小黑的猫" : "用户要求称呼为大爷");
        var questions = List.of("我们家的猫叫什么名字？", "一加一等于1。", "给我讲一个故事。", "吃了吧。", "刚才讲的故事叫什么名字？");
        var answers = List.of("小黑。", "一加一等于二。", "一只狐狸煮了一锅面，你猜它吃了吗？", "对，狐狸吃了。", "狐狸的晚餐。");
        for (int i = 0; i < questions.size(); i++) {
            pipeline.beginTurn("t" + i, i + 1); pipeline.speechStarted(); pipeline.speechStopped();
            asr.emit(new ProviderEvent.TranscriptDone(questions.get(i)));
            var messages = chat.requests.get(i).messages();
            if (i == 1 || i == 2) {
                assertEquals(2, messages.size());
                assertFalse(messages.stream().anyMatch(m -> m.content().contains("小黑")));
            }
            if (i == 3) {
                assertTrue(messages.stream().anyMatch(m -> m.content().equals(answers.get(2))));
                assertFalse(messages.stream().anyMatch(m -> m.content().contains("小黑") || m.content().contains("一加一")));
            }
            if (i == 4) assertTrue(messages.stream().anyMatch(m -> m.content().equals(answers.get(2))));
            chat.emit(new ProviderEvent.TextDelta(answers.get(i))); chat.emit(new ProviderEvent.TextDone(answers.get(i)));
            tts.listener.onEvent(new ProviderEvent.AudioDone());
        }
        pipeline.close();
    }

    private static final class IncrementalTts implements TtsClient {
        int opens, finished, cancelled, immediateFailures;
        boolean rejectInput;
        final List<String> texts = new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.concurrent.CountDownLatch submitted = new java.util.concurrent.CountDownLatch(1);
        TtsListener listener;
        public String providerType() { return "QWEN"; }
        public TtsStream stream(TtsRequest request, TtsListener listener) { throw new AssertionError("one connection per turn expected"); }
        public TtsSession openSession(ResolvedModel model, TtsListener listener) {
            opens++; this.listener = listener;
            if (opens <= immediateFailures) listener.onEvent(new ProviderEvent.ProviderError("tts_connect_failed", "failed", true));
            return new TtsSession() {
                public void appendText(String value) {
                    if (rejectInput) throw new IllegalStateException("input closed");
                    texts.add(value); submitted.countDown();
                }
                public void finishInput() { finished++; }
                public void cancel() { cancelled++; }
            };
        }
    }

    private static ResolvedModel asrModel(){return new ResolvedModel(11,303,403,"QWEN","ASR","asr","wss://asr","{}","{}","k");}
    private static ResolvedModel chatModel(){return new ResolvedModel(11,301,401,"DEEPSEEK","CHAT","chat","https://chat","{}","{}","k");}
    private static ResolvedModel ttsModel(){return new ResolvedModel(11,304,404,"QWEN","TTS","tts","wss://tts","{}","{}","k");}

    private static final class FakeAsrClient implements AsrClient {
        AsrListener listener; int cancelCount;
        @Override public String providerType(){return "QWEN";}
        @Override public AsrSession open(ResolvedModel m,AsrListener l){listener=l;return new AsrSession(){
            public void appendAudio(ByteBuffer p){}
            public void speechStarted(){}
            public void speechStopped(){}
            public void cancel(){cancelCount++;}
        };}
        void emit(ProviderEvent e){listener.onEvent(e);}
    }
    private static final class FakeChatClient implements ChatModelClient {
        final List<ChatRequest> requests=new ArrayList<>(); ChatListener listener; int cancelCount;
        @Override public String providerType(){return "DEEPSEEK";}
        @Override public ChatStream stream(ChatRequest r,ChatListener l){requests.add(r);listener=l;return ()->cancelCount++;}
        void emit(ProviderEvent e){listener.onEvent(e);}
    }
    private static final class FakeTtsClient implements TtsClient {
        final List<String> texts=new ArrayList<>(); final List<TtsListener> listeners=new ArrayList<>(); final List<Integer> cancelCounts=new ArrayList<>();
        @Override public String providerType(){return "QWEN";}
        @Override public TtsStream stream(TtsRequest r,TtsListener l){texts.add(r.text());listeners.add(l);cancelCounts.add(0);int i=cancelCounts.size()-1;return ()->cancelCounts.set(i,cancelCounts.get(i)+1);}
        void emit(int i,ProviderEvent e){listeners.get(i).onEvent(e);}
    }
}
