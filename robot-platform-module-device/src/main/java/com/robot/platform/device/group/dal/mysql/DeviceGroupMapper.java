package com.robot.platform.device.group.dal.mysql;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.device.group.dal.dataobject.DeviceGroupDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface DeviceGroupMapper extends BaseMapperX<DeviceGroupDO> { default DeviceGroupDO selectByIdAndTenantId(long id, long tenantId) { return selectOne(DeviceGroupDO::getId, id, DeviceGroupDO::getTenantId, tenantId); } }
