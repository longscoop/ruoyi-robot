package com.robot.platform.integration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.robot.platform.device.auth.service.DeviceSession;
import com.robot.platform.device.auth.service.DeviceSessionTokenService;
import com.robot.platform.device.identity.service.DeviceHttpAuthenticationIdentity;
import com.robot.platform.device.identity.service.DeviceIdentity;
import com.robot.platform.device.identity.service.DeviceIdentityService;
import com.robot.platform.member.auth.service.MemberSession;
import com.robot.platform.member.auth.service.MemberSessionTokenService;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.SubjectType;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the actual server filter chain and Redis stores; token namespaces cannot authenticate each other's APIs. */
@Import(ApiAudienceIsolationIT.Ports.class)
@TestPropertySource(properties = {
        "robot.security.secret-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "yudao.security.token-header=X-Access-Token",
        "yudao.security.token-parameter=access_token",
        "yudao.security.mock-enable=true",
        "yudao.security.mock-secret=admin-control-"
})
class ApiAudienceIsolationIT extends AbstractRobotPlatformIntegrationTest {
    @Autowired private TestRestTemplate http;
    @Autowired private DeviceSessionTokenService deviceSessions;
    @Autowired private MemberSessionTokenService memberSessions;

    @Test
    void actualSecurityChainEnforcesAudienceAndConfiguredAdminTokenCarriers() {
        String device = deviceSessions.issue(new DeviceSession(10L, 7L, 9L, "SN-001", 4, ApiAudience.DEVICE));
        String app = memberSessions.issue(new MemberSession(10L, 8L, ApiAudience.APP, SubjectType.MEMBER));

        // APP and DEVICE filters precede legacy authentication on their own namespaces.
        assertThat(get("/device-api/test/probe", device).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/app-api/test/probe", app).getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String token : new String[]{app, "system-opaque-token"}) assertUnauthorized("/device-api/test/probe", token);
        for (String token : new String[]{device, "system-opaque-token"}) assertUnauthorized("/app-api/test/probe", token);
        // Valid upstream ADMIN credentials cannot authenticate APP or DEVICE endpoints.
        assertUnauthorized(getWithHeader("/device-api/test/probe", "X-Access-Token", "admin-control-1"));
        assertUnauthorized(getWithHeader("/app-api/test/probe", "X-Access-Token", "admin-control-1"));

        // Reject credential smuggling rather than letting upstream ADMIN authentication overwrite an APP/DEVICE principal.
        assertUnauthorized(getWithAuthorizationAndHeader("/device-api/test/probe", device, "admin-control-1"));
        assertUnauthorized(getWithAuthorizationAndHeader("/app-api/test/probe", app, "admin-control-1"));

        // The admin guard shares upstream extraction: its configured header and query carrier cannot bypass it.
        assertUnauthorized("/admin-api/test/probe", device);
        assertUnauthorized("/admin-api/test/probe", app);
        assertUnauthorized(getWithQuery("/admin-api/test/probe", "access_token", app));
        assertUnauthorized(getWithHeader("/admin-api/test/probe", "X-Access-Token", device));

        // An upstream-authenticated SYSTEM_USER still succeeds through the same configured carrier.
        assertThat(getWithHeader("/admin-api/test/probe", "X-Access-Token", "admin-control-1").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
    private void assertUnauthorized(String path, String token) {
        assertThat(get(path, token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    private void assertUnauthorized(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    private ResponseEntity<String> getWithQuery(String path, String parameter, String token) {
        return http.exchange(path + "?" + parameter + "=" + token, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }
    private ResponseEntity<String> getWithHeader(String path, String header, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(header, token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
    private ResponseEntity<String> getWithAuthorizationAndHeader(String path, String robotToken, String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(robotToken);
        headers.set("X-Access-Token", adminToken);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @TestConfiguration
    @MapperScan(basePackages = {
            "com.robot.platform.tenant.quota.dal.mysql",
            "com.robot.platform.member.member.dal.mysql", "com.robot.platform.member.binding.dal.mysql",
            "com.robot.platform.device.device.dal.mysql", "com.robot.platform.device.group.dal.mysql",
            "com.robot.platform.device.product.dal.mysql", "com.robot.platform.robot.robot.dal.mysql"})
    static class Ports {
        @Bean @Primary DeviceIdentityService deviceIdentityService() {
            DeviceHttpAuthenticationIdentity identity = new DeviceHttpAuthenticationIdentity(7L, 10L, 9L,
                    "SN-001", 4, "not-used-by-session-resolution");
            return new DeviceIdentityService() {
                @Override public DeviceIdentity findBySn(String deviceSn) {
                    return new DeviceIdentity(7L, 10L, 3L, "SN-001", "mqtt", 4);
                }
                @Override public DeviceHttpAuthenticationIdentity findForHttpAuthentication(String deviceSn) {
                    return "SN-001".equals(deviceSn) ? identity : null;
                }
            };
        }
        @RestController
        @TenantIgnore
        static class ProbeController {
            @GetMapping("/device-api/test/probe") public CommonResult<Boolean> device() { return CommonResult.success(true); }
            @GetMapping("/app-api/test/probe") public CommonResult<Boolean> app() { return CommonResult.success(true); }
            @GetMapping("/admin-api/test/probe") public CommonResult<Boolean> admin() { return CommonResult.success(true); }
        }
    }
}
