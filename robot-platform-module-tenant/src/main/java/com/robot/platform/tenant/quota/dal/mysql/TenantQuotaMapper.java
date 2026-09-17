package com.robot.platform.tenant.quota.dal.mysql;

import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.tenant.quota.dal.dataobject.TenantQuotaDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantQuotaMapper extends BaseMapperX<TenantQuotaDO> {
    /** Creates a lockable quota row exactly once, including concurrent first configuration. */
    @Insert("""
            INSERT INTO tenant_quota (tenant_id, robot_limit, creator, create_time, updater, update_time, deleted)
            VALUES (#{tenantId}, 0, '', NOW(), '', NOW(), b'0')
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("tenantId") long tenantId);

    default TenantQuotaDO selectByTenantId(long tenantId) {
        return selectOne(TenantQuotaDO::getTenantId, tenantId);
    }

    /** Must be called inside the service transaction. */
    default TenantQuotaDO selectByTenantIdForUpdate(long tenantId) {
        return selectOneForUpdate(TenantQuotaDO::getTenantId, tenantId);
    }
}
