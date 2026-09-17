package com.robot.platform.robot.message.inbox.dal.mysql;

import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.robot.message.inbox.dal.dataobject.RobotMessageInboxDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RobotMessageInboxMapper extends BaseMapperX<RobotMessageInboxDO> {
    @Select("SELECT * FROM robot_message_inbox WHERE tenant_id=#{tenantId} AND device_id=#{deviceId} AND message_id=#{messageId} AND deleted=0")
    RobotMessageInboxDO selectByMessageId(@Param("tenantId") long tenantId, @Param("deviceId") long deviceId, @Param("messageId") String messageId);

    @Update("UPDATE robot_message_inbox SET result=#{result} WHERE tenant_id=#{tenantId} AND device_id=#{deviceId} AND message_id=#{messageId} AND deleted=0")
    int updateResult(@Param("tenantId") long tenantId, @Param("deviceId") long deviceId, @Param("messageId") String messageId, @Param("result") String result);
}
