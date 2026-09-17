package com.robot.platform.robot.robot.service;

import com.robot.platform.member.binding.service.RobotOwnershipVerifier;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.member.member.enums.MemberErrorCodeConstants.MEMBER_BINDING_INVALID;

/** Robot-side implementation keeps ownership checks with the robot aggregate and avoids a module cycle. */
@Component
@RequiredArgsConstructor
public class RobotOwnershipVerifierImpl implements RobotOwnershipVerifier {
    private final RobotMapper robotMapper;
    @Override public void requireOwnedByTenant(long tenantId, long robotId) {
        RobotDO robot = robotMapper.selectById(robotId);
        if (robot == null || !Long.valueOf(tenantId).equals(robot.getTenantId())) throw exception(MEMBER_BINDING_INVALID);
    }
}
