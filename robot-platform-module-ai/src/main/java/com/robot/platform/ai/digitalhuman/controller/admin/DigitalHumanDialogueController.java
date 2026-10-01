package com.robot.platform.ai.digitalhuman.controller.admin;

import com.robot.platform.ai.digitalhuman.service.DigitalHumanDialogueSessions;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static com.robot.platform.framework.common.pojo.CommonResult.success;
import static com.robot.platform.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@RestController
@RequestMapping("/ai/digital-humans/{id}/dialogue-sessions")
@PreAuthorize("@ss.hasPermission('ai:digital-human:preview')")
@RequiredArgsConstructor
public class DigitalHumanDialogueController {
    private final DigitalHumanDialogueSessions sessions;
    @PostMapping public CommonResult<String> open(@PathVariable long id) { return success(sessions.open(tenant(), user(), id)); }
    @PostMapping(value = "/{sessionId}/turns", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @com.robot.platform.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    public SseEmitter turn(@PathVariable long id, @PathVariable String sessionId, @RequestBody Audio audio) {
        return sessions.turn(tenant(), user(), id, sessionId, audio.pcm());
    }
    @PostMapping("/{sessionId}/interrupt") public CommonResult<Boolean> interrupt(@PathVariable long id, @PathVariable String sessionId) {
        sessions.interrupt(tenant(), user(), id, sessionId); return success(true);
    }
    @PostMapping("/{sessionId}/keepalive") public CommonResult<Boolean> keepalive(@PathVariable long id, @PathVariable String sessionId) {
        sessions.touch(tenant(), user(), id, sessionId); return success(true);
    }
    @DeleteMapping("/{sessionId}") public CommonResult<Boolean> close(@PathVariable long id, @PathVariable String sessionId) {
        sessions.close(tenant(), user(), id, sessionId); return success(true);
    }
    private static long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private static long user() { Long id = getLoginUserId(); if (id == null) throw invalidParamException("请先登录"); return id; }
    public record Audio(String pcm) {}
}
