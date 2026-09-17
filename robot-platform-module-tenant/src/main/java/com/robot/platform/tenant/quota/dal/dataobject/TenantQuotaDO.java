package com.robot.platform.tenant.quota.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/** Tenant robot capacity. Mapper methods always filter by tenantId explicitly. */
@TableName("tenant_quota")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantQuotaDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Integer robotLimit;
}
