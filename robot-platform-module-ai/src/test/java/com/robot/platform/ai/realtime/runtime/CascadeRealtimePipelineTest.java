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
        assertEquals("你是小优。",chat.requests.get(0).messages().get(0).content());
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
