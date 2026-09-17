package com.robot.platform.member.binding.service;

import com.robot.platform.member.binding.dal.dataobject.MemberRobotBindingDO;
import com.robot.platform.member.binding.dal.mysql.MemberRobotBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.member.member.enums.MemberErrorCodeConstants.MEMBER_ROBOT_ACCESS_DENIED;

/** Access is decided from the complete tenant/member/robot binding key, never a robot id alone. */
@Service
@RequiredArgsConstructor
public class MemberRobotAccessServiceImpl implements MemberRobotAccessService {
    private final MemberRobotBindingMapper bindingMapper;
    @Override public void requireReadable(long tenantId, long memberId, long robotId) { require(tenantId, memberId, robotId, false); }
    @Override public void requireControllable(long tenantId, long memberId, long robotId) { require(tenantId, memberId, robotId, true); }
    private void require(long tenantId, long memberId, long robotId, boolean control) {
        MemberRobotBindingDO binding = bindingMapper.selectByTenantMemberAndRobot(tenantId, memberId, robotId);
        if (binding == null || !"ENABLED".equals(binding.getStatus())
                || (control && !("OWNER".equals(binding.getRole()) || "OPERATOR".equals(binding.getRole())))) {
            throw exception(MEMBER_ROBOT_ACCESS_DENIED);
        }
    }
}
