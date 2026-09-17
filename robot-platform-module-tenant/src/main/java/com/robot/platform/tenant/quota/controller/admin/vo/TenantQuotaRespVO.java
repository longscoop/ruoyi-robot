package com.robot.platform.tenant.quota.controller.admin.vo;

import lombok.Data;

@Data
public class TenantQuotaRespVO {
    private Long tenantId;
    private Integer robotLimit;
    private Integer robotUsed;
}
