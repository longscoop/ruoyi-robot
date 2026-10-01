package com.robot.platform.ai.conversation.controller.admin;

import com.robot.platform.ai.conversation.dal.dataobject.*;
import com.robot.platform.ai.conversation.dal.mysql.*;
import com.robot.platform.ai.realtime.dal.dataobject.AiRealtimeSessionDO;
import com.robot.platform.ai.realtime.dal.mysql.AiRealtimeSessionMapper;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.*;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.robot.platform.framework.common.pojo.CommonResult.success;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@RestController @RequestMapping("/ai/conversations") @RequiredArgsConstructor
public class AiConversationAdminController {
    private final AiConversationMapper conversations;
    private final AiConversationMessageMapper messages;
    private final AiRealtimeSessionMapper sessions;

    @GetMapping @PreAuthorize("@ss.hasPermission('ai:conversation:query')")
    public CommonResult<List<ConversationResp>> list() {
        long tenant = TenantContextHolder.getRequiredTenantId();
        var grouped = sessions.selectAllByTenantId(tenant).stream()
                .collect(Collectors.groupingBy(AiRealtimeSessionDO::getConversationId));
        return success(conversations.selectAllByTenantId(tenant).stream()
                .map(row -> response(row, grouped.getOrDefault(row.getId(), List.of()))).toList());
    }

    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('ai:conversation:query')")
    public CommonResult<ConversationDetail> get(@PathVariable long id) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        AiConversationDO row = conversations.selectByIdAndTenantId(id, tenant);
        if (row == null) throw invalidParamException("对话不存在");
        var related = sessions.selectAllByTenantId(tenant).stream().filter(s -> s.getConversationId().equals(id)).toList();
        return success(new ConversationDetail(response(row, related), messages.selectByConversationIdAndTenantId(id, tenant)));
    }
    private static ConversationResp response(AiConversationDO row, List<AiRealtimeSessionDO> related) {
        ConversationResp result = new ConversationResp();
        BeanUtils.copyProperties(row, result);
        result.setRealtimeSessions(related);
        return result;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class ConversationResp extends AiConversationDO {
        private List<AiRealtimeSessionDO> realtimeSessions;
    }
    public record ConversationDetail(ConversationResp conversation, List<AiConversationMessageDO> messages) {}
}
