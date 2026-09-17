package com.robot.platform.member.binding;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.robot.platform.member.binding.dal.dataobject.MemberRobotBindingDO;
import com.robot.platform.member.binding.dal.mysql.MemberRobotBindingMapper;
import com.robot.platform.member.binding.service.MemberRobotAccessService;
import com.robot.platform.member.binding.service.MemberRobotAccessServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Each access decision must be scoped by the authenticated tenant, member and robot together. */
class MemberRobotAccessServiceTest {
    private final MemberRobotBindingMapper bindingMapper = mock(MemberRobotBindingMapper.class);
    private final MemberRobotAccessService access = new MemberRobotAccessServiceImpl(bindingMapper);

    @Test
    void permitsReadingAnEnabledBinding() {
        when(bindingMapper.selectByTenantMemberAndRobot(10L, 7L, 99L)).thenReturn(binding("READ", true));

        assertThatCode(() -> access.requireReadable(10L, 7L, 99L)).doesNotThrowAnyException();
    }

    @Test
    void permitsControlOnlyForOwnerOrOperator() {
        when(bindingMapper.selectByTenantMemberAndRobot(10L, 7L, 99L)).thenReturn(binding("OWNER", true));
        assertThatCode(() -> access.requireControllable(10L, 7L, 99L)).doesNotThrowAnyException();
        when(bindingMapper.selectByTenantMemberAndRobot(10L, 7L, 99L)).thenReturn(binding("OPERATOR", true));
        assertThatCode(() -> access.requireControllable(10L, 7L, 99L)).doesNotThrowAnyException();
    }

    @Test
    void deniesInactiveOrUnboundRobot() {
        when(bindingMapper.selectByTenantMemberAndRobot(10L, 7L, 99L)).thenReturn(binding("OWNER", false));
        assertThatThrownBy(() -> access.requireReadable(10L, 7L, 99L)).isInstanceOf(ServiceException.class);
        when(bindingMapper.selectByTenantMemberAndRobot(10L, 7L, 99L)).thenReturn(null);
        assertThatThrownBy(() -> access.requireReadable(10L, 7L, 99L)).isInstanceOf(ServiceException.class);
    }

    @Test
    void deniesCrossTenantBindingByNeverLookingUpRobotAlone() {
        when(bindingMapper.selectByTenantMemberAndRobot(10L, 7L, 99L)).thenReturn(null);

        assertThatThrownBy(() -> access.requireReadable(10L, 7L, 99L)).isInstanceOf(ServiceException.class);
        verify(bindingMapper).selectByTenantMemberAndRobot(10L, 7L, 99L);
        verifyNoMoreInteractions(bindingMapper);
    }

    private static MemberRobotBindingDO binding(String role, boolean enabled) {
        return MemberRobotBindingDO.builder().id(1L).tenantId(10L).memberId(7L).robotId(99L)
                .role(role).status(enabled ? "ENABLED" : "DISABLED").build();
    }
}
