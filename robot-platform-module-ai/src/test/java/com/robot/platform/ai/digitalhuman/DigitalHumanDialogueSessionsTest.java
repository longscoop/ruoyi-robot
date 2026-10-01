package com.robot.platform.ai.digitalhuman;

import com.robot.platform.ai.digitalhuman.controller.admin.DigitalHumanDialogueController;
import com.robot.platform.ai.digitalhuman.service.*;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.model.client.event.ProviderEvent;
import com.robot.platform.framework.security.core.LoginUser;
import com.robot.platform.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.framework.web.config.*;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DigitalHumanDialogueSessionsTest {
    DigitalHumanDialogueFactory factory;
    DigitalHumanDialogueSessions sessions;
    RealtimeProviderSession provider;
    RealtimeTurnListener listener;
    AtomicLong generation;
    MockMvc mvc;
    String id;
    static final String PCM = Base64.getEncoder().encodeToString(new byte[6400]);

    @BeforeEach void setup() {
        factory = mock(DigitalHumanDialogueFactory.class);
        provider = mock(RealtimeProviderSession.class);
        generation = new AtomicLong();
        when(factory.open(eq(1L), eq(42L), any())).thenAnswer(call -> { listener = call.getArgument(2); return provider; });
        doAnswer(call -> { generation.set(call.getArgument(1)); return null; }).when(provider).beginTurn(anyString(), anyLong());
        sessions = new DigitalHumanDialogueSessions(factory);
        id = sessions.open(1, 7, 42);
        var web = new RobotPlatformWebAutoConfiguration().webMvcRegistrations(new WebProperties());
        mvc = MockMvcBuilders.standaloneSetup(new DigitalHumanDialogueController(sessions))
                .setCustomHandlerMapping(web::getRequestMappingHandlerMapping).build();
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L), new org.springframework.mock.web.MockHttpServletRequest());
    }
    @AfterEach void cleanup() { sessions.destroy(); TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    MvcResult startTurn() throws Exception {
        MvcResult result = mvc.perform(post("/admin-api/ai/digital-humans/42/dialogue-sessions/" + id + "/turns")
                .contentType("application/json").content("{\"pcm\":\"" + PCM + "\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        verify(provider, timeout(2000).atLeastOnce()).speechStopped();
        return result;
    }
    void emit(ProviderEvent event) { listener.onEvent(String.valueOf(generation.get()), generation.get(), event); }

    @Test void streamsTranscriptTextAndAudioThroughProductionRoute() throws Exception {
        MvcResult result = startTurn();
        emit(new ProviderEvent.TranscriptDone("你好"));
        emit(new ProviderEvent.TextDelta("你好呀"));
        emit(new ProviderEvent.AudioDelta(ByteBuffer.wrap(new byte[]{1, 2, 3, 4})));
        emit(new ProviderEvent.AudioDone());
        String body = mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("user.done")); assertTrue(body.contains("你好"));
        assertTrue(body.contains("assistant.delta")); assertTrue(body.contains("AQIDBA=="));
        assertTrue(body.contains("24000")); assertTrue(body.contains("done"));
        verify(provider).appendAudio(argThat(bytes -> bytes.remaining() == 6400));
    }
    @Test void rejectsAccessFromDifferentTenantUserOrHuman() {
        assertThrows(RuntimeException.class, () -> sessions.touch(2, 7, 42, id));
        assertThrows(RuntimeException.class, () -> sessions.interrupt(1, 8, 42, id));
        assertThrows(RuntimeException.class, () -> sessions.close(1, 7, 43, id));
        sessions.touch(1, 7, 42, id);
    }
    @Test void rejectsMalformedShortOddAndOversizedAudio() {
        for (String pcm : new String[]{"?", "", Base64.getEncoder().encodeToString(new byte[3201]), "A".repeat(1_280_001)})
            assertThrows(RuntimeException.class, () -> sessions.turn(1, 7, 42, id, pcm));
        verify(provider, never()).speechStarted();
    }
    @Test void interruptionFiltersLateEventsAndAllowsNextTurn() throws Exception {
        MvcResult first = startTurn();
        long stale = generation.get();
        sessions.interrupt(1, 7, 42, id);
        verify(provider, timeout(2000)).cancelCurrentResponse();
        mvc.perform(asyncDispatch(first)).andExpect(content().string(org.hamcrest.Matchers.containsString("interrupted")));
        clearInvocations(provider);
        MvcResult second = startTurn();
        listener.onEvent(String.valueOf(stale), stale, new ProviderEvent.TextDelta("stale-response"));
        emit(new ProviderEvent.TextDone("fresh-response")); emit(new ProviderEvent.AudioDone());
        String body = mvc.perform(asyncDispatch(second)).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("stale-response")); assertTrue(body.contains("fresh-response"));
    }
    @Test void boundsSessionsAndClosesProvider() {
        sessions.open(1, 7, 42);
        assertThrows(RuntimeException.class, () -> sessions.open(1, 7, 42));
        sessions.close(1, 7, 42, id);
        verify(provider, timeout(2000)).close();
        assertThrows(RuntimeException.class, () -> sessions.touch(1, 7, 42, id));
    }
    @Test void interruptAfterAudioDoneDoesNotCancelIdleProvider() throws Exception {
        MvcResult result = startTurn();
        emit(new ProviderEvent.AudioDone());
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        sessions.interrupt(1, 7, 42, id);
        sessions.close(1, 7, 42, id);
        verify(provider, timeout(2000)).close();
        verify(provider, never()).cancelCurrentResponse();
    }
    @Test void providerErrorDoesNotExposeCredentialsAndEndsStream() throws Exception {
        MvcResult result = startTurn();
        emit(new ProviderEvent.ProviderError("upstream", "secret=do-not-expose", false));
        String body = mvc.perform(asyncDispatch(result)).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("error")); assertFalse(body.contains("do-not-expose"));
    }
    @Test void providerOpenedAfterDestroyIsStillClosed() throws Exception {
        CountDownLatch opening = new CountDownLatch(1), proceed = new CountDownLatch(1);
        when(factory.open(eq(1L), eq(42L), any())).thenAnswer(call -> { opening.countDown(); assertTrue(proceed.await(2, TimeUnit.SECONDS)); return provider; });
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> assertThrows(RuntimeException.class, () -> sessions.open(1, 8, 42)));
        assertTrue(opening.await(2, TimeUnit.SECONDS)); sessions.destroy(); proceed.countDown(); future.get(3, TimeUnit.SECONDS);
        verify(provider, timeout(2000).times(2)).close();
    }
}
