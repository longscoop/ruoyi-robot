package com.robot.platform.tenant.quota.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.tenant.quota.dal.dataobject.TenantUsageDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantUsageMapper extends BaseMapperX<TenantUsageDO> {
    /** Creates a lockable usage row exactly once, matching quota initialization order. */
    @Insert("""
            INSERT INTO tenant_usage (tenant_id, robot_used, creator, create_time, updater, update_time, deleted)
            VALUES (#{tenantId}, 0, '', NOW(), '', NOW(), b'0')
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("tenantId") long tenantId);

    default TenantUsageDO selectByTenantId(long tenantId) {
        return selectOne(TenantUsageDO::getTenantId, tenantId);
    }

    /** Must be called after the quota lock, in the same transaction. */
    default TenantUsageDO selectByTenantIdForUpdate(long tenantId) {
        return selectOneForUpdate(TenantUsageDO::getTenantId, tenantId);
    }
}
