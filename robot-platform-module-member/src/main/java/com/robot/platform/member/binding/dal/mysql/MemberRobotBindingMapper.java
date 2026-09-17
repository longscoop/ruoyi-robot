package com.robot.platform.member.binding.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.member.binding.dal.dataobject.MemberRobotBindingDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface MemberRobotBindingMapper extends BaseMapperX<MemberRobotBindingDO> {
    default MemberRobotBindingDO selectByTenantMemberAndRobot(long tenantId, long memberId, long robotId) {
        return selectOne(MemberRobotBindingDO::getTenantId, tenantId, MemberRobotBindingDO::getMemberId, memberId,
                MemberRobotBindingDO::getRobotId, robotId);
    }
    default java.util.List<MemberRobotBindingDO> selectByTenantAndMember(long tenantId, long memberId) {
        return selectList(MemberRobotBindingDO::getTenantId, tenantId, MemberRobotBindingDO::getMemberId, memberId);
    }
    default java.util.List<MemberRobotBindingDO> selectByTenantId(long tenantId) {
        return selectList(MemberRobotBindingDO::getTenantId, tenantId);
    }
    @Delete("DELETE FROM member_robot_binding WHERE id = #{id} AND tenant_id = #{tenantId}")
    int physicalDeleteByIdAndTenantId(long id, long tenantId);
}
