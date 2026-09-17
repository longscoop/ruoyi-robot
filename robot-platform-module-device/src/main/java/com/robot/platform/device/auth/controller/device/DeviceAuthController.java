package com.robot.platform.device.auth.controller.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.robot.platform.device.auth.controller.device.vo.DeviceTokenReqVO;
import com.robot.platform.device.auth.controller.device.vo.DeviceTokenRespVO;
import com.robot.platform.device.auth.DeviceTokenAuthenticationException;
import com.robot.platform.device.auth.service.DeviceTokenRequest;
import com.robot.platform.device.auth.service.DeviceTokenResult;
import com.robot.platform.device.auth.service.DeviceTokenService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Device login is the sole anonymous DEVICE route; tenant identity is resolved from the authenticated device. */
@RestController
@RequiredArgsConstructor
public class DeviceAuthController {
    private final DeviceTokenService tokens;

    @PostMapping("/device-api/auth/token")
    @PermitAll
    @TenantIgnore
    public CommonResult<DeviceTokenRespVO> token(@Valid @RequestBody DeviceTokenReqVO request) {
        DeviceTokenResult result = tokens.issue(new DeviceTokenRequest(request.getDeviceSn(), request.getTimestamp(),
                request.getNonce(), request.getSignature()));
        return success(new DeviceTokenRespVO(result.accessToken(), result.audience()));
    }

    @ExceptionHandler(DeviceTokenAuthenticationException.class)
    public ResponseEntity<Void> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
