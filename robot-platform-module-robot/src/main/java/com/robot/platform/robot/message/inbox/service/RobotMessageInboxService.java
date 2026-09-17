package com.robot.platform.robot.message.inbox.service;

import com.robot.platform.robot.message.inbox.dal.dataobject.RobotMessageInboxDO;

public interface RobotMessageInboxService {
    enum InsertResult { INSERTED, DUPLICATE, CONFLICT }
    InsertResult insertOrVerify(RobotMessageInboxDO inbox);
    default boolean insertIfAbsent(RobotMessageInboxDO inbox) { return insertOrVerify(inbox) == InsertResult.INSERTED; }
    void markResult(RobotMessageInboxDO inbox, String result);
}
