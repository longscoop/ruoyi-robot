package com.robot.platform.robot.message.inbox.service;

import com.robot.platform.robot.message.inbox.dal.dataobject.RobotMessageInboxDO;
import com.robot.platform.robot.message.inbox.dal.mysql.RobotMessageInboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service @RequiredArgsConstructor
public class RobotMessageInboxServiceImpl implements RobotMessageInboxService {
    private final RobotMessageInboxMapper mapper;
    @Override public InsertResult insertOrVerify(RobotMessageInboxDO inbox) {
        try {
            return mapper.insert(inbox) == 1 ? InsertResult.INSERTED : InsertResult.CONFLICT;
        } catch (DuplicateKeyException duplicate) {
            RobotMessageInboxDO existing = mapper.selectByMessageId(inbox.getTenantId(), inbox.getDeviceId(), inbox.getMessageId());
            if (existing == null) throw duplicate;
            return sameFact(existing, inbox) ? InsertResult.DUPLICATE : InsertResult.CONFLICT;
        }
    }
    @Override public void markResult(RobotMessageInboxDO inbox, String result) {
        if (mapper.updateResult(inbox.getTenantId(), inbox.getDeviceId(), inbox.getMessageId(), result) != 1) {
            throw new IllegalStateException("accepted inbox message disappeared");
        }
    }
    private static boolean sameFact(RobotMessageInboxDO left, RobotMessageInboxDO right) {
        return Objects.equals(left.getRequestId(), right.getRequestId()) && Objects.equals(left.getMessageType(), right.getMessageType())
                && Objects.equals(left.getPayloadHash(), right.getPayloadHash());
    }
}
