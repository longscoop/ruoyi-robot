package com.robot.platform.member.binding.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.member.binding.dal.dataobject.MemberRobotBindingDO;
import com.robot.platform.member.binding.dal.mysql.MemberRobotBindingMapper;
import com.robot.platform.member.member.dal.mysql.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.member.member.enums.MemberErrorCodeConstants.*;

/** Admin-managed grants are idempotent and always scoped to the current tenant. */
@Service
@RequiredArgsConstructor
public class MemberRobotBindingServiceImpl implements MemberRobotBindingService {
    private final MemberRobotBindingMapper bindingMapper;
    private final MemberMapper memberMapper;
    private final RobotOwnershipVerifier robotOwnershipVerifier;

    @Override @Transactional(rollbackFor = Exception.class)
    public long bind(MemberRobotBindCommand command) {
        requireCurrentTenant(command.tenantId());
        if (memberMapper.selectByIdAndTenantId(command.memberId(), command.tenantId()) == null) throw exception(MEMBER_NOT_EXISTS);
        robotOwnershipVerifier.requireOwnedByTenant(command.tenantId(), command.robotId());
        MemberRobotBindingDO existing = bindingMapper.selectByTenantMemberAndRobot(command.tenantId(), command.memberId(), command.robotId());
        if (existing != null) {
            existing.setRole(command.role()); existing.setStatus(command.status()); bindingMapper.updateById(existing);
            return existing.getId();
        }
        MemberRobotBindingDO binding = MemberRobotBindingDO.builder().tenantId(command.tenantId()).memberId(command.memberId())
                .robotId(command.robotId()).role(command.role()).status(command.status()).build();
        try { bindingMapper.insert(binding); return binding.getId(); }
        catch (DuplicateKeyException collision) {
            MemberRobotBindingDO winner = bindingMapper.selectByTenantMemberAndRobot(command.tenantId(), command.memberId(), command.robotId());
            if (winner == null) throw collision;
            winner.setRole(command.role()); winner.setStatus(command.status()); bindingMapper.updateById(winner);
            return winner.getId();
        }
    }
    @Override public List<MemberRobotBindingDO> listMine(long tenantId, long memberId) {
        requireCurrentTenant(tenantId);
        return bindingMapper.selectByTenantAndMember(tenantId, memberId);
    }
    @Override public List<MemberRobotBindingDO> list(long tenantId) {
        requireCurrentTenant(tenantId);
        return bindingMapper.selectByTenantId(tenantId);
    }
    @Override public void remove(long tenantId, long bindingId) {
        requireCurrentTenant(tenantId);
        if (bindingMapper.physicalDeleteByIdAndTenantId(bindingId, tenantId) == 0) throw exception(MEMBER_BINDING_INVALID);
    }
    private static void requireCurrentTenant(long tenantId) {
        if (!Long.valueOf(tenantId).equals(TenantContextHolder.getTenantId())) throw exception(MEMBER_TENANT_FORBIDDEN);
    }
}
