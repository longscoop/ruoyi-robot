package com.robot.platform.member.binding.dal.dataobject;

import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/** A tenant-local grant from a member to a robot. */
@TableName("member_robot_binding")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberRobotBindingDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private Long memberId;
    private Long robotId;
    private String role;
    private String status;
}
