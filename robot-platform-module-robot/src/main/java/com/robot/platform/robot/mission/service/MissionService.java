package com.robot.platform.robot.mission.service;

import com.robot.platform.framework.common.pojo.PageResult;
import com.robot.platform.robot.mission.service.command.MissionCancelCommand;
import com.robot.platform.robot.mission.service.command.MissionCreateCommand;
import com.robot.platform.robot.mission.service.command.MissionPageQuery;
import com.robot.platform.robot.mission.service.dto.MissionRespDTO;
import com.robot.platform.robot.mission.service.dto.MissionDetailDTO;
import com.robot.platform.robot.mission.message.MissionAckPayload;
import com.robot.platform.robot.mission.message.MissionEventPayload;
import com.robot.platform.robot.mission.message.MessageHandleResult;

public interface MissionService {
    MissionRespDTO create(MissionCreateCommand command);
    MissionRespDTO get(long id);
    MissionDetailDTO detail(long id);
    PageResult<MissionRespDTO> page(MissionPageQuery query);
    void cancel(long id, MissionCancelCommand command);
    void timeoutPending(long id);
    void dispatchPending(long id);
    void commandDeliveryExhausted(long missionId, String commandType, String errorCode, String errorMessage);
    MessageHandleResult acknowledge(long deviceId, String requestId, String messageId, MissionAckPayload payload);
    MessageHandleResult handleAction(long deviceId, String requestId, String messageId, MissionEventPayload payload);
    MessageHandleResult handleResult(long deviceId, String requestId, String messageId, MissionEventPayload payload);
}
