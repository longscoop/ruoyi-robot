package com.robot.platform.ai.model.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@TableName("ai_model_provider")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelProviderDO extends BaseDO {
    @TableId
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String providerType;
    private String baseUrl;
    private String apiKeyCiphertext;
    private String configJson;
    private String status;
}
