package com.robot.platform.member.member.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.member.member.dal.dataobject.MemberDO;
import com.robot.platform.member.member.dal.mysql.MemberMapper;
import com.robot.platform.member.member.enums.MemberStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Tenant administrators manage member accounts without exposing the password hash. */
@RestController @RequestMapping("/member/members") @Validated @RequiredArgsConstructor
public class MemberAdminController {
    private final MemberMapper members; private final PasswordEncoder passwordEncoder;
    @PostMapping @PreAuthorize("@ss.hasPermission('member:member:create')")
    public CommonResult<Long> create(@Valid @RequestBody CreateReqVO request) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        MemberDO member = MemberDO.builder().tenantId(tenantId).mobile(request.getMobile()).nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword())).status(request.getStatus()).build();
        members.insert(member); return success(member.getId());
    }
    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('member:member:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody UpdateReqVO request) {
        MemberDO member = members.selectByIdAndTenantId(id, TenantContextHolder.getRequiredTenantId());
        if (member == null) return success(false);
        member.setMobile(request.getMobile()); member.setNickname(request.getNickname()); member.setStatus(request.getStatus());
        if (request.getPassword() != null && !request.getPassword().isBlank()) member.setPassword(passwordEncoder.encode(request.getPassword()));
        members.updateById(member); return success(true);
    }
    @GetMapping @PreAuthorize("@ss.hasPermission('member:member:query')")
    public CommonResult<List<MemberRespVO>> list() {
        return success(members.selectByTenantId(TenantContextHolder.getRequiredTenantId()).stream()
                .map(member -> new MemberRespVO(member.getId(), member.getMobile(), member.getNickname(), member.getStatus())).toList());
    }
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('member:member:query')")
    public CommonResult<MemberRespVO> get(@PathVariable long id) {
        MemberDO member = members.selectByIdAndTenantId(id, TenantContextHolder.getRequiredTenantId());
        return success(member == null ? null : new MemberRespVO(member.getId(), member.getMobile(), member.getNickname(), member.getStatus()));
    }
    @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermission('member:member:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        MemberDO member = members.selectByIdAndTenantId(id, TenantContextHolder.getRequiredTenantId());
        if (member != null) members.deleteById(id);
        return success(true);
    }
    @Data public static class MemberBaseReqVO { @NotBlank private String mobile; private String nickname; private String status = MemberStatus.ENABLED.name(); }
    @Data public static class CreateReqVO extends MemberBaseReqVO { @NotBlank private String password; }
    @Data public static class UpdateReqVO extends MemberBaseReqVO { private String password; }
    public record MemberRespVO(long id, String mobile, String nickname, String status) { }
}
