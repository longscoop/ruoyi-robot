package com.robot.platform.ai.memory.identity;

import com.robot.platform.ai.realtime.protocol.RealtimeClientEvent;
import com.robot.platform.member.binding.service.MemberRobotAccessService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationIdentityResolverTest {

    private final MemberRobotAccessService accessService = mock(MemberRobotAccessService.class);
    private final ConversationIdentityResolver resolver = new ConversationIdentityResolver(accessService, 0.85);

    @Test
    void acceptsHighConfidenceVoiceprintOnlyAfterActiveBindingAuthorization() {
        ConversationIdentity identity = resolver.resolve(11L, 33L,
                new RealtimeClientEvent.CandidateIdentity(99L, "VOICEPRINT", 0.92));

        assertEquals(99L, identity.memberId());
        assertEquals("VOICEPRINT", identity.identityType());
        assertTrue(identity.memberMemoryAllowed());
        verify(accessService).requireReadable(11L, 99L, 33L);
    }

    @Test
    void lowConfidenceBecomesAnonymousWithoutBindingLookup() {
        ConversationIdentity identity = resolver.resolve(11L, 33L,
                new RealtimeClientEvent.CandidateIdentity(99L, "VOICEPRINT", 0.84));

        assertNull(identity.memberId());
        assertFalse(identity.memberMemoryAllowed());
        verifyNoInteractions(accessService);
    }

    @Test
    void unboundOrCrossTenantCandidateIsNeverTrusted() {
        doThrow(new IllegalArgumentException("binding not found"))
                .when(accessService).requireReadable(11L, 99L, 33L);

        ConversationIdentity identity = resolver.resolve(11L, 33L,
                new RealtimeClientEvent.CandidateIdentity(99L, "FACE", 0.96));

        assertNull(identity.memberId());
        assertEquals("ANONYMOUS", identity.identityType());
        assertFalse(identity.memberMemoryAllowed());
    }

    @Test
    void explicitAnonymousAlwaysClearsMemberId() {
        ConversationIdentity identity = resolver.resolve(11L, 33L,
                new RealtimeClientEvent.CandidateIdentity(99L, "ANONYMOUS", 1.0));

        assertNull(identity.memberId());
        verifyNoInteractions(accessService);
    }
}
