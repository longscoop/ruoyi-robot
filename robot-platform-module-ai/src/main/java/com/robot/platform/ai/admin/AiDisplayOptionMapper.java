package com.robot.platform.ai.admin;

import com.robot.platform.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** Only identifiers and display names; every query is explicitly tenant scoped. */
@Mapper
public interface AiDisplayOptionMapper {
    record Option(Long id, String name) {}
    @TenantIgnore @Select("SELECT id,name FROM ai_agent WHERE tenant_id=#{tenant} AND deleted=0 ORDER BY name")
    List<Option> agents(@Param("tenant") long tenant);
    @TenantIgnore @Select("SELECT id,name FROM robot WHERE tenant_id=#{tenant} AND deleted=0 ORDER BY name")
    List<Option> robots(@Param("tenant") long tenant);
    @TenantIgnore @Select("SELECT id,nickname AS name FROM member WHERE tenant_id=#{tenant} AND deleted=0 ORDER BY nickname")
    List<Option> members(@Param("tenant") long tenant);
    @TenantIgnore @Select("SELECT id,name FROM ai_model_provider WHERE tenant_id=#{tenant} AND deleted=0 ORDER BY name")
    List<Option> providers(@Param("tenant") long tenant);
}
