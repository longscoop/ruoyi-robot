package com.robot.platform.integration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.robot.platform.member.binding.dal.dataobject.MemberRobotBindingDO;
import com.robot.platform.member.binding.dal.mysql.MemberRobotBindingMapper;
import com.robot.platform.member.binding.service.MemberRobotAccessService;
import com.robot.platform.member.binding.service.MemberRobotBindCommand;
import com.robot.platform.member.binding.service.MemberRobotBindingService;
import com.robot.platform.member.member.dal.dataobject.MemberDO;
import com.robot.platform.member.member.dal.mysql.MemberMapper;
import com.robot.platform.robot.robot.dal.dataobject.RobotDO;
import com.robot.platform.robot.robot.dal.mysql.RobotMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real MySQL proof that a member grant never escapes its tenant/member/robot composite key. */
@Import(MemberRobotTenantIsolationIT.Ports.class)
@TestPropertySource(properties = "robot.security.secret-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class MemberRobotTenantIsolationIT extends AbstractRobotPlatformIntegrationTest {
    @Autowired private MemberMapper memberMapper;
    @Autowired private MemberRobotBindingMapper bindingMapper;
    @Autowired private MemberRobotAccessService access;
    @Autowired private MemberRobotBindingService bindings;
    @Autowired private RobotMapper robotMapper;

    @Test
    void tenantAMemberCannotReadUnboundFamilyOrTenantBRobot() {
        long alice = member(10L, "13800138000");
        long bob = member(10L, "13800138001");
        long unbound = robot(10L, "A-unbound", 100L);
        long ownRobot = robot(10L, "A-own", 102L);
        long familyRobot = robot(10L, "A-family", 101L);
        long foreignRobot = robot(20L, "B-foreign", 200L);
        TenantUtils.execute(10L, () -> bindingMapper.insert(MemberRobotBindingDO.builder().tenantId(10L).memberId(bob)
                .robotId(familyRobot).role("OWNER").status("ENABLED").build()));

        TenantUtils.execute(10L, () -> {
            bindings.bind(new MemberRobotBindCommand(10L, alice, ownRobot, "READ", "ENABLED"));
            access.requireReadable(10L, alice, ownRobot);
            assertThatThrownBy(() -> access.requireReadable(10L, alice, unbound)).isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> access.requireReadable(10L, alice, familyRobot)).isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> access.requireReadable(10L, alice, foreignRobot)).isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> bindings.bind(new MemberRobotBindCommand(10L, alice, foreignRobot, "READ", "ENABLED")))
                    .isInstanceOf(ServiceException.class);
        });
    }

    private long member(long tenantId, String mobile) {
        return TenantUtils.execute(tenantId, () -> {
            MemberDO member = MemberDO.builder().tenantId(tenantId).mobile(mobile).password("$2a$10$storedStrongHashOnly")
                    .nickname(mobile).status("ENABLED").build();
            memberMapper.insert(member); return member.getId();
        });
    }
    private long robot(long tenantId, String code, long deviceId) {
        return TenantUtils.execute(tenantId, () -> {
            RobotDO robot = RobotDO.builder().tenantId(tenantId).deviceId(deviceId).productId(3L).robotCode(code)
                    .name(code).onlineStatus("OFFLINE").workStatus("IDLE").build();
            robotMapper.insert(robot); return robot.getId();
        });
    }
    @org.springframework.boot.test.context.TestConfiguration
    @MapperScan(basePackages = {
            "com.robot.platform.member.member.dal.mysql", "com.robot.platform.member.binding.dal.mysql",
            "com.robot.platform.tenant.quota.dal.mysql", "com.robot.platform.device.device.dal.mysql",
            "com.robot.platform.device.group.dal.mysql", "com.robot.platform.device.product.dal.mysql",
            "com.robot.platform.robot.robot.dal.mysql"})
    static class Ports { }
}
