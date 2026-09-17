package com.robot.platform.device.device.controller.admin.vo;
import lombok.Data;
import java.time.LocalDateTime;
/** Deliberately contains no secret, hash, or ciphertext fields. */
@Data public class DeviceRespVO {
    private Long id; private Long tenantId; private Long productId; private Long robotId; private String deviceSn; private String name;
    private String lifecycleStatus; private Integer credentialVersion; private String mqttUsername; private LocalDateTime createTime; private LocalDateTime activateTime; private LocalDateTime lastBindTime;
}
