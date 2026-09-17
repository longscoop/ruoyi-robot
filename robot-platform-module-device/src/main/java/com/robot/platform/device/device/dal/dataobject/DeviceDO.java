package com.robot.platform.device.device.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDateTime;

/** Secret-bearing persistence object: never serialize or log it. */
@TableName("device")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"mqttSecretHash", "httpSecretCiphertext"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long productId;
    private Long robotId;
    private String deviceSn;
    private String name;
    private String lifecycleStatus;
    private Integer credentialVersion;
    private String mqttUsername;
    private String mqttSecretHash;
    private String httpSecretCiphertext;
    private LocalDateTime activateTime;
    private LocalDateTime lastBindTime;
}
