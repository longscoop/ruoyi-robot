package com.robot.platform.member.member.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.member.member.dal.dataobject.MemberDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberMapper extends BaseMapperX<MemberDO> {
    default MemberDO selectByTenantAndMobile(long tenantId, String mobile) {
        return selectOne(MemberDO::getTenantId, tenantId, MemberDO::getMobile, mobile);
    }
    default MemberDO selectByIdAndTenantId(long memberId, long tenantId) {
        return selectOne(MemberDO::getId, memberId, MemberDO::getTenantId, tenantId);
    }
    default java.util.List<MemberDO> selectByTenantId(long tenantId) {
        return selectList(MemberDO::getTenantId, tenantId);
    }
}
