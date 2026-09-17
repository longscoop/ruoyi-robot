package com.robot.platform.robot.status.dal.redis;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.robot.platform.robot.robot.enums.RobotOnlineStatus;
import com.robot.platform.robot.status.model.RobotLiveStatus;
import com.robot.platform.robot.status.service.RobotLiveStatusStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Redis projection stores an explicit schemaVersion and atomically guards ONLINE -> OFFLINE. */
@Component @RequiredArgsConstructor
public class RedisRobotLiveStatusStore implements RobotLiveStatusStore {
    private final StringRedisTemplate redis;
    @Override public Optional<RobotLiveStatus> get(long tenantId, long robotId) {
        String value = redis.opsForValue().get(RobotLiveStatusStore.key(tenantId, robotId));
        return Optional.ofNullable(value).map(v -> {
            RobotLiveStatus decoded = JsonUtils.parseObject(v, RobotLiveStatus.class);
            // The canonical epoch field is also used by the Lua CAS. Reconstructing Instant from it
            // avoids ObjectMapper timestamp-unit drift between application configurations.
            Instant heartbeat = decoded.lastHeartbeatEpochMillis() == 0 ? decoded.lastHeartbeatAt()
                    : Instant.ofEpochMilli(decoded.lastHeartbeatEpochMillis());
            return new RobotLiveStatus(decoded.schemaVersion(), decoded.onlineStatus(), decoded.workStatus(),
                    decoded.batteryLevel(), decoded.cpuUsage(), decoded.memoryUsage(), decoded.temperatureCelsius(),
                    decoded.ipAddress(), decoded.currentMissionId(), decoded.softwareVersion(), heartbeat,
                    decoded.lastHeartbeatEpochMillis());
        });
    }
    @Override public void put(long tenantId, long robotId, RobotLiveStatus status, Duration ttl) {
        redis.opsForValue().set(RobotLiveStatusStore.key(tenantId, robotId), JsonUtils.toJsonString(status), ttl);
    }
    @Override public boolean restoreOfflineIfAbsent(long tenantId, long robotId, RobotLiveStatus offline, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(RobotLiveStatusStore.key(tenantId, robotId),
                JsonUtils.toJsonString(offline), ttl));
    }
    @Override public boolean transitionOffline(long tenantId, long robotId, int expectedSchemaVersion,
                                               Instant expectedLastHeartbeatAt, Instant cutoff, Duration ttl) {
        String key = RobotLiveStatusStore.key(tenantId, robotId);
        Long transitioned = redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "local raw=redis.call('GET',KEYS[1]); if not raw then return 0 end; "
                        + "local ok,v=pcall(cjson.decode,raw); if not ok then return 0 end; "
                        + "if tonumber(v.schemaVersion)~=tonumber(ARGV[1]) or v.onlineStatus~='ONLINE' then return 0 end; "
                        + "local hb=tonumber(v.lastHeartbeatEpochMillis); if not hb or hb~=tonumber(ARGV[2]) or hb>tonumber(ARGV[3]) then return 0 end; "
                        + "v.onlineStatus='OFFLINE'; redis.call('SET',KEYS[1],cjson.encode(v),'PX',ARGV[4]); return 1",
                Long.class), java.util.List.of(key), String.valueOf(expectedSchemaVersion),
                String.valueOf(expectedLastHeartbeatAt.toEpochMilli()), String.valueOf(cutoff.toEpochMilli()),
                String.valueOf(ttl.toMillis()));
        return Long.valueOf(1L).equals(transitioned);
    }
}
