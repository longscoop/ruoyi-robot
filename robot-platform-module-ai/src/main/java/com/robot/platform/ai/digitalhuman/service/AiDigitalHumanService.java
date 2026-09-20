package com.robot.platform.ai.digitalhuman.service;

import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanActionDO;
import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
import java.util.List;

public interface AiDigitalHumanService {
    AiDigitalHumanDO create(Command command);
    AiDigitalHumanDO update(long id, Command command);
    AiDigitalHumanDO get(long tenantId, long id);
    List<AiDigitalHumanDO> list(long tenantId);
    void delete(long tenantId, long id);
    List<AiDigitalHumanActionDO> replaceActions(long tenantId, long digitalHumanId, List<ActionCommand> actions);

    record Command(long tenantId,String name,String code,String description,long agentId,String avatarType,
                   String avatarUrl,String avatarResourceUrl,String coverUrl,Long voiceModelId,String voiceId,
                   java.math.BigDecimal speechRate,java.math.BigDecimal pitch,java.math.BigDecimal volume,
                   String lipSyncMode,String welcomeText,boolean interruptEnabled,String configJson,String status){}
    record ActionCommand(String state,String actionCode,String configJson){}
}
