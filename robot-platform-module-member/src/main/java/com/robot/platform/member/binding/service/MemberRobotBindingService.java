package com.robot.platform.member.binding.service;

import com.robot.platform.member.binding.dal.dataobject.MemberRobotBindingDO;
import java.util.List;

public interface MemberRobotBindingService {
    long bind(MemberRobotBindCommand command);
    List<MemberRobotBindingDO> listMine(long tenantId, long memberId);
    List<MemberRobotBindingDO> list(long tenantId);
    void remove(long tenantId, long bindingId);
}
