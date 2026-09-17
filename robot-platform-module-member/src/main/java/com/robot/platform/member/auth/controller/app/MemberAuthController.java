package com.robot.platform.member.auth.controller.app;

import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.security.core.LoginUser;
import com.robot.platform.framework.security.core.util.SecurityFrameworkUtils;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.member.auth.service.*;
import com.robot.platform.member.binding.dal.dataobject.MemberRobotBindingDO;
import com.robot.platform.member.binding.service.MemberRobotBindingService;
import com.robot.platform.member.member.dal.dataobject.MemberDO;
import com.robot.platform.member.member.dal.mysql.MemberMapper;
import com.robot.platform.security.ApiAudience;
import com.robot.platform.security.RobotAuthenticatedPrincipal;
import com.robot.platform.security.SubjectType;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.robot.platform.framework.common.pojo.CommonResult.success;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.member.member.enums.MemberErrorCodeConstants.MEMBER_NOT_EXISTS;

/** APP endpoints derive member and tenant exclusively from the typed member session after login. */
@RestController
@Validated
@RequiredArgsConstructor
public class MemberAuthController {
    private final MemberAuthenticationProvider authenticationProvider;
    private final MemberSessionTokenService sessions;
    private final MemberMapper memberMapper;
    private final MemberRobotBindingService bindings;

    @PostMapping("/auth/login") @PermitAll
    public CommonResult<LoginRespVO> login(@Valid @RequestBody LoginReqVO request) {
        MemberSession session = authenticationProvider.authenticate(new MemberLoginCommand(
                TenantContextHolder.getRequiredTenantId(), request.getMobile(), request.getPassword()));
        return success(new LoginRespVO(sessions.issue(session)));
    }
    @PostMapping("/auth/logout")
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String raw = request.getHeader(HttpHeaders.AUTHORIZATION);
        sessions.revoke(raw != null && raw.startsWith("Bearer ") ? raw.substring(7).trim() : raw);
        return success(true);
    }
    @GetMapping("/member/profile")
    public CommonResult<MemberProfileRespVO> profile() {
        RobotAuthenticatedPrincipal principal = requireMemberPrincipal();
        MemberDO member = memberMapper.selectByIdAndTenantId(principal.getId(), principal.getTenantId());
        if (member == null) throw exception(MEMBER_NOT_EXISTS);
        return success(new MemberProfileRespVO(member.getId(), member.getMobile(), member.getNickname(), member.getStatus()));
    }
    @GetMapping("/member/robot-bindings")
    public CommonResult<List<BindingRespVO>> robotBindings() {
        RobotAuthenticatedPrincipal principal = requireMemberPrincipal();
        return success(bindings.listMine(principal.getTenantId(), principal.getId()).stream()
                .map(binding -> new BindingRespVO(binding.getRobotId(), binding.getRole(), binding.getStatus())).toList());
    }
    private static RobotAuthenticatedPrincipal requireMemberPrincipal() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (!(user instanceof RobotAuthenticatedPrincipal principal) || principal.getAudience() != ApiAudience.APP
                || principal.getSubjectType() != SubjectType.MEMBER) throw exception(MEMBER_NOT_EXISTS);
        return principal;
    }
    @Data public static class LoginReqVO { @NotBlank private String mobile; @NotBlank private String password; }
    public record LoginRespVO(String accessToken) { }
    public record MemberProfileRespVO(long id, String mobile, String nickname, String status) { }
    public record BindingRespVO(long robotId, String role, String status) { }
}
