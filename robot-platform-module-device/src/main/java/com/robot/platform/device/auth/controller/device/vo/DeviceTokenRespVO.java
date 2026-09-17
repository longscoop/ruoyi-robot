package com.robot.platform.device.auth.controller.device.vo;

import com.robot.platform.security.ApiAudience;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/** Opaque access token response. Do not write this object to application logs. */
@Getter
@AllArgsConstructor
@ToString(exclude = "accessToken")
public class DeviceTokenRespVO {
    private final String accessToken;
    private final ApiAudience audience;
    private final String tokenType = "Bearer";
}
