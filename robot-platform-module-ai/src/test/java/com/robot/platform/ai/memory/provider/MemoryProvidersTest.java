package com.robot.platform.ai.memory.provider;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.extract.*;
import com.robot.platform.ai.memory.extract.MemoryExtractor.CompletedTurn;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.*;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemoryProvidersTest {
    @TempDir Path directory;
    private static AiAgentConfig agent(String mode,long tenant) {
        return new AiAgentConfig(1,tenant,"a","角色",1,1,"CASCADE",2L,null,3L,4L,mode,true,true);
    }
    @Test void localSummarySurvivesRestartAndNeverCrossesTenantRobotOrUnverifiedMember() throws Exception {
        var extractor=mock(MemoryExtractor.class);
        when(extractor.extract(any(),any(),any(),any())).thenReturn(List.of(
                new MemoryCandidate("ROBOT","PREFERENCE","用户喜欢咖啡少糖",.9,.95,null),
                new MemoryCandidate("ROBOT","WORK","用户是一位程序员",.8,.9,null)));
        var properties=new MemoryProviderProperties();properties.setLocalDirectory(directory.toString());
        var parser=new MemoryDirectiveParser();var policy=new DefaultMemoryPolicy(.75,.6);
        var local=new LocalShortMemoryProvider(extractor,properties,parser,policy);
        local.saveMemory(ConversationIdentity.anonymous(1,2),agent("MEM_LOCAL_SHORT",1),List.of(new CompletedTurn("我喜欢少糖咖啡，是程序员","你养了一只猫")));
        String yaml=Files.readString(directory.resolve("tenant-1-agent-1-robot-2.yaml"));
        assertTrue(yaml.contains("PREFERENCE"));assertFalse(yaml.contains("养了一只猫"));
        var reopened=new LocalShortMemoryProvider(extractor,properties,parser,policy);
        assertEquals(1,reopened.queryMemory(ConversationIdentity.anonymous(1,2),agent("MEM_LOCAL_SHORT",1),"工作",8).size());
        assertTrue(reopened.queryMemory(ConversationIdentity.anonymous(2,2),agent("MEM_LOCAL_SHORT",2),"工作",8).isEmpty());
        assertTrue(reopened.queryMemory(ConversationIdentity.anonymous(1,3),agent("MEM_LOCAL_SHORT",1),"工作",8).isEmpty());
        assertTrue(reopened.queryMemory(new ConversationIdentity(1,2,3L,"VOICE",.99,true),agent("MEM_LOCAL_SHORT",1),"工作",8).isEmpty());
        assertEquals(1,reopened.queryMemory(new ConversationIdentity(1,2,3L,"CLAIM",.2,false),agent("MEM_LOCAL_SHORT",1),"工作",8).size());
        assertThrows(IllegalArgumentException.class,()->local.queryMemory(ConversationIdentity.anonymous(2,2),agent("MEM_LOCAL_SHORT",1),"工作",8));
    }
    @Test void doNotRememberAndExpiredAndFailedExtractionDoNotDestroyExistingSummary() {
        var extractor=mock(MemoryExtractor.class);var properties=new MemoryProviderProperties();properties.setLocalDirectory(directory.toString());
        var local=new LocalShortMemoryProvider(extractor,properties,new MemoryDirectiveParser(),new DefaultMemoryPolicy(.75,.6));
        when(extractor.extract(any(),any(),any(),any())).thenReturn(List.of(new MemoryCandidate("ROBOT","FACT","用户养猫",.9,.95,null)));
        var i=ConversationIdentity.anonymous(1,2);var a=agent("MEM_LOCAL_SHORT",1);
        local.saveMemory(i,a,List.of(new CompletedTurn("我养猫","好")));
        reset(extractor);
        local.saveMemory(i,a,List.of(new CompletedTurn("不要记住我住址","好")));
        verifyNoInteractions(extractor);
        when(extractor.extract(any(),any(),any(),any())).thenReturn(List.of());
        local.saveMemory(i,a,List.of(new CompletedTurn("这是闲聊","好")));
        assertEquals("用户养猫",local.queryMemory(i,a,"猫",8).get(0).content());
    }
    @Test void localForgetRequiresAuthorizedScopeAndRemovesOnlyTheRequestedFact() {
        var extractor=mock(MemoryExtractor.class);var props=new MemoryProviderProperties();props.setLocalDirectory(directory.toString());
        var local=new LocalShortMemoryProvider(extractor,props,new MemoryDirectiveParser(),new DefaultMemoryPolicy(.75,.6));
        var i=ConversationIdentity.anonymous(1,2);var a=agent("MEM_LOCAL_SHORT",1);
        when(extractor.extract(any(),any(),any(),any())).thenReturn(List.of(new MemoryCandidate("ROBOT","FACT","用户养猫",.9,.95,null),new MemoryCandidate("ROBOT","WORK","用户是程序员",.9,.95,null)));
        local.saveMemory(i,a,List.of(new CompletedTurn("我养猫，是程序员","好")));
        when(extractor.extract(any(),any(),any(),any())).thenReturn(List.of(new MemoryCandidate("MEMBER","FACT","用户养猫",.9,.95,null)));
        local.saveMemory(i,a,List.of(new CompletedTurn("忘记我的猫","好")));
        assertEquals(1,local.queryMemory(i,a,"猫",8).size());
        when(extractor.extract(any(),any(),any(),any())).thenReturn(List.of(new MemoryCandidate("ROBOT","FACT","用户养猫",.9,.95,null)));
        local.saveMemory(i,a,List.of(new CompletedTurn("忘记我的猫","好")));
        assertEquals(List.of("用户是程序员"),local.queryMemory(i,a,"工作",8).stream().map(com.robot.platform.ai.memory.service.MemorySnippet::content).toList());
    }

    @Test void queryRemainsFastWhileTheBackgroundSummarizerIsBlocked() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        MemoryExtractor extractor=(turn,identity,directive)->{
            if(calls.incrementAndGet()>1){entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            return List.of(new MemoryCandidate("ROBOT","FACT","用户养猫",.9,.95,null));
        };
        var props=new MemoryProviderProperties();props.setLocalDirectory(directory.toString());
        var local=new LocalShortMemoryProvider(extractor,props,new MemoryDirectiveParser(),new DefaultMemoryPolicy(.75,.6));
        var i=ConversationIdentity.anonymous(1,2);var a=agent("MEM_LOCAL_SHORT",1);
        local.saveMemory(i,a,List.of(new CompletedTurn("我养猫","好")));
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        var saving=pool.submit(()->local.saveMemory(i,a,List.of(new CompletedTurn("我喜欢少糖","好"))));
        try {
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofMillis(500),()->assertEquals(1,local.queryMemory(i,a,"猫",8).size()));
        }finally{release.countDown();saving.get(2,java.util.concurrent.TimeUnit.SECONDS);pool.shutdownNow();}
    }

    @Test void noMemoryNeverTouchesStorageAndFailedQueryFallsBackToEmpty() {
        var failing=mock(MemoryProvider.class);when(failing.name()).thenReturn("powermem");
        when(failing.queryMemory(any(),any(),anyString(),anyInt())).thenThrow(new IllegalStateException("offline"));
        var registry=new MemoryProviderRegistry(List.of(new NoMemoryProvider(),failing),new MemoryProviderProperties());
        var i=ConversationIdentity.anonymous(1,2);
        assertTrue(registry.queryMemory(i,agent("NOMEM",1),"猫",8).isEmpty());
        registry.saveMemory(i,agent("NOMEM",1),List.of(new CompletedTurn("我养猫","好")));
        verify(failing,never()).queryMemory(any(),any(),anyString(),anyInt());
        verify(failing,never()).saveMemory(any(),any(),anyList());
        assertTrue(registry.queryMemory(i,agent("POWERMEM",1),"猫",8).isEmpty());
    }
    @Test void slowProviderHasBoundedWaitAndIsCancelled() throws Exception {
        var provider = mock(MemoryProvider.class); when(provider.name()).thenReturn("powermem");
        var interrupted = new java.util.concurrent.CountDownLatch(1);
        when(provider.queryMemory(any(), any(), anyString(), anyInt())).thenAnswer(call -> {
            try { new java.util.concurrent.CountDownLatch(1).await(); }
            catch (InterruptedException e) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return List.of();
        });
        var props = new MemoryProviderProperties(); props.setQueryDeadlineMillis(80);
        var registry = new MemoryProviderRegistry(List.of(provider), props);
        try {
            assertTimeoutPreemptively(java.time.Duration.ofMillis(500), () ->
                    assertTrue(registry.queryMemory(ConversationIdentity.anonymous(1,2), agent("POWERMEM",1), "猫", 3).isEmpty()));
            assertTrue(interrupted.await(1, java.util.concurrent.TimeUnit.SECONDS));
        } finally { registry.destroy(); }
    }

    @Test void mem0SearchIsScopedAndSaveUsesOnlyUserEvidenceAndExcludesPrivacyDirective() {
        var props=new MemoryProviderProperties();props.getMem0().setEnabled(true);props.getMem0().setApiKey("test-key");
        var http=mock(RemoteMemoryHttp.class);
        when(http.request(any(),eq("POST"),eq("/v3/memories/search/"),any(),anyInt(),eq("Token")))
                .thenReturn(JsonUtils.parseTree("{\"results\":[{\"id\":\"x\",\"user_id\":\"tenant-1-agent-1-robot-2\",\"memory\":\"用户爱咖啡\"},{\"user_id\":\"another-user\",\"memory\":\"other secret\"}]}"));
        var provider=new Mem0MemoryProvider(props,http,new MemoryDirectiveParser(),mock(MemoryForgetTargets.class));
        var i=ConversationIdentity.anonymous(1,2);var a=agent("MEM0AI",1);
        var recalled=provider.queryMemory(i,a,"咖啡",8);
        assertEquals(1,recalled.size());assertEquals("用户爱咖啡",recalled.get(0).content());
        verify(http).request(any(),eq("POST"),eq("/v3/memories/search/"),argThat(body -> body.toString().contains("tenant-1-agent-1-robot-2")),anyInt(),eq("Token"));
        provider.saveMemory(i,a,List.of(new CompletedTurn("我爱咖啡","你住在上海"),new CompletedTurn("不要记住这句私人内容","好")));
        verify(http).request(any(),eq("POST"),eq("/v3/memories/add/"),argThat(body -> body.toString().contains("我爱咖啡")&&!body.toString().contains("上海")&&!body.toString().contains("私人内容")),anyInt(),eq("Token"));
    }
    @Test void powerMemNeverInjectsWholeProfileAndRejectsOtherNamespace() {
        var props=new MemoryProviderProperties();props.getPowermem().setEnabled(true);props.getPowermem().setApiKey("token");
        var http=mock(RemoteMemoryHttp.class);
        when(http.request(any(),eq("POST"),eq("/query"),any(),anyInt(),eq("Bearer"))).thenReturn(JsonUtils.parseTree("{\"profile\":\"用户是一名程序员\",\"results\":[{\"memory\":\"用户喜欢少糖\"},{\"user_id\":\"other\",\"memory\":\"secret\"}]}"));
        var provider=new PowerMemMemoryProvider(props,http,new MemoryDirectiveParser(),mock(MemoryForgetTargets.class));
        var rows=provider.queryMemory(ConversationIdentity.anonymous(1,2),agent("POWERMEM",1),"职业",8);
        assertEquals(1,rows.size());assertEquals("FACT",rows.get(0).memoryType());
        assertFalse(rows.stream().anyMatch(r -> r.content().contains("程序员")));
    }
}
