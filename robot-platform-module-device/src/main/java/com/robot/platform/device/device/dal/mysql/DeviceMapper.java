package com.robot.platform.device.device.dal.mysql;

import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DeviceMapper extends BaseMapperX<DeviceDO> {
    default DeviceDO selectByDeviceSn(String deviceSn) {
        return selectOne(DeviceDO::getDeviceSn, deviceSn);
    }
    /** MQTT callbacks authenticate an exact, server-issued username rather than a client-supplied tenant. */
    default DeviceDO selectByMqttUsername(String mqttUsername) {
        return selectOne(DeviceDO::getMqttUsername, mqttUsername);
    }
    default DeviceDO selectByDeviceSnForUpdate(String deviceSn) {
        return selectOneForUpdate(DeviceDO::getDeviceSn, deviceSn);
    }
    default DeviceDO selectByIdForUpdate(long id) {
        return selectOneForUpdate(DeviceDO::getId, id);
    }
    default java.util.List<DeviceDO> selectByTenantId(long tenantId) {
        return selectList(new LambdaQueryWrapperX<DeviceDO>().eq(DeviceDO::getTenantId, tenantId));
    }
    default java.util.List<DeviceDO> selectInventory() {
        return selectList(new LambdaQueryWrapperX<DeviceDO>().isNull(DeviceDO::getTenantId));
    }

    /**
     * The global NOT_NULL update strategy cannot represent an unbind: every nullable
     * binding/credential column must be persisted as NULL in one guarded write.
     */
    @Update("""
            UPDATE device
            SET tenant_id = NULL, robot_id = NULL, lifecycle_status = 'UNACTIVATED',
                credential_version = #{nextCredentialVersion}, mqtt_username = NULL,
                mqtt_secret_hash = NULL, http_secret_ciphertext = NULL,
                activate_time = NULL, last_bind_time = NULL, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{deviceId} AND tenant_id = #{tenantId}
              AND credential_version = #{expectedCredentialVersion} AND deleted = 0
            """)
    int resetToInventoryAfterUnbind(@Param("deviceId") long deviceId,
                                    @Param("tenantId") long tenantId,
                                    @Param("expectedCredentialVersion") int expectedCredentialVersion,
                                    @Param("nextCredentialVersion") int nextCredentialVersion);
}
