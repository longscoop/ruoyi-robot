package com.robot.platform.robot.message.inbox;

import com.robot.platform.robot.message.inbox.dal.dataobject.RobotMessageInboxDO;
import com.robot.platform.robot.message.inbox.dal.mysql.RobotMessageInboxMapper;
import com.robot.platform.robot.message.inbox.service.RobotMessageInboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RobotMessageInboxServiceTest {
    private final RobotMessageInboxMapper mapper = mock(RobotMessageInboxMapper.class);
    private final RobotMessageInboxServiceImpl service = new RobotMessageInboxServiceImpl(mapper);
    private final RobotMessageInboxDO message = new RobotMessageInboxDO();

    RobotMessageInboxServiceTest() {
        message.setTenantId(10L); message.setDeviceId(20L); message.setMessageId("01K5K8VJGR9VK8T3H7ZQX3W1AB");
        message.setRequestId("request-1"); message.setMessageType("MISSION_EVENT"); message.setPayloadHash("hash");
    }

    @Test
    void duplicateUniqueKeyIsTheOnlyIgnoredInsertFailure() {
        when(mapper.insert(message)).thenThrow(new DuplicateKeyException("duplicate message"));
        when(mapper.selectByMessageId(anyLong(), anyLong(), any())).thenReturn(message);

        assertThat(service.insertIfAbsent(message)).isFalse();
    }

    @Test
    void invalidRowsAndDatabaseFailuresRemainVisibleForRetry() {
        when(mapper.insert(message)).thenThrow(new DataIntegrityViolationException("request_id is null"));

        assertThatThrownBy(() -> service.insertIfAbsent(message))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("request_id");
    }

    @Test
    void reusedMessageIdWithDifferentPayloadIsAConflictNotADuplicate() {
        RobotMessageInboxDO existing = new RobotMessageInboxDO();
        existing.setTenantId(10L); existing.setDeviceId(20L); existing.setMessageId(message.getMessageId());
        existing.setRequestId("request-1"); existing.setMessageType("MISSION_EVENT"); existing.setPayloadHash("other-hash");
        when(mapper.insert(message)).thenThrow(new DuplicateKeyException("duplicate message"));
        when(mapper.selectByMessageId(10L, 20L, message.getMessageId())).thenReturn(existing);

        assertThat(service.insertOrVerify(message)).isEqualTo(com.robot.platform.robot.message.inbox.service.RobotMessageInboxService.InsertResult.CONFLICT);
    }
}
