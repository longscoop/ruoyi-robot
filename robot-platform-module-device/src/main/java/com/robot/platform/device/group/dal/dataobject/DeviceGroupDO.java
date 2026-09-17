package com.robot.platform.device.group.dal.dataobject;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
@TableName("device_group") @Data @EqualsAndHashCode(callSuper = true) @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceGroupDO extends BaseDO { @TableId private Long id; private Long tenantId; private String name; private String remark; }
