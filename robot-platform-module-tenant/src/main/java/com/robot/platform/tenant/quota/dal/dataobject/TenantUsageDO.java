package com.robot.platform.tenant.quota.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/** Persisted robot use, reconciled from device rows when operational repair is required. */
@TableName("tenant_usage")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUsageDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Integer robotUsed;
}
