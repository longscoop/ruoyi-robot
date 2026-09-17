package com.robot.platform.device.group.dal.mysql;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.robot.platform.device.group.dal.dataobject.DeviceGroupRelationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
@Mapper public interface DeviceGroupRelationMapper extends BaseMapperX<DeviceGroupRelationDO> {
    @Delete("DELETE FROM device_group_relation WHERE group_id = #{groupId} AND device_id = #{deviceId}") int physicalDeleteByGroupAndDevice(long groupId, long deviceId);
    @Delete("DELETE FROM device_group_relation WHERE group_id = #{groupId}") int physicalDeleteByGroup(long groupId);
}
