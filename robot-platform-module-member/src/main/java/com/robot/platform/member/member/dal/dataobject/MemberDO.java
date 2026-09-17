package com.robot.platform.member.member.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/** Tenant-scoped member account. Passwords are BCrypt hashes and must never leave persistence. */
@TableName("member")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "password")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDO extends BaseDO {
    @TableId private Long id;
    private Long tenantId;
    private String mobile;
    private String password;
    private String nickname;
    private String status;
}
