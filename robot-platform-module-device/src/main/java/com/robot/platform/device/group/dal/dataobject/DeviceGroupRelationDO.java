package com.robot.platform.device.group.dal.dataobject;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
@TableName("device_group_relation") @Data @EqualsAndHashCode(callSuper = true) @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceGroupRelationDO extends BaseDO { @TableId private Long id; private Long tenantId; private Long groupId; private Long deviceId; }
