package com.robot.platform.robot.command.outbox.service;

import com.robot.platform.robot.command.outbox.dal.dataobject.RobotCommandOutboxDO;

/** Domain port: transport retry exhaustion is converted into a Mission decision by Mission code. */
public interface RobotCommandDeliveryFailureHandler {
    void exhausted(RobotCommandOutboxDO command, String errorCode, String errorMessage);
}
