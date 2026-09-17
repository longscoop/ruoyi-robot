package com.robot.platform.tenant.quota.controller.admin.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class TenantQuotaUpdateReqVO {
    @NotNull(message = "机器人配额不能为空")
    @PositiveOrZero(message = "机器人配额不能为负数")
    private Integer robotLimit;
}
