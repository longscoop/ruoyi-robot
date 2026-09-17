package com.robot.platform.framework.redis.config;

import com.robot.platform.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotPlatformRedisAutoConfigurationTest {

    private static final String LEGACY_PRIMARY_PREFIX = "cn.iocoder." + "yu" + "dao.";
    private static final String LEGACY_BOOT_PREFIX = "cn.iocoder." + "boot.";

    @Test
    void deserializesLegacyPrimaryTypeIdAsCurrentRobotPlatformClass() {
        RedisSerializer<?> serializer = RobotPlatformRedisAutoConfiguration.buildRedisSerializer();
        byte[] legacyPayload = ("{\"@class\":\"" + LEGACY_PRIMARY_PREFIX + "framework.common.pojo.CommonResult\","
                + "\"code\":0,\"msg\":\"ok\",\"data\":\"cached\"}").getBytes(StandardCharsets.UTF_8);

        Object value = serializer.deserialize(legacyPayload);

        assertThat(value).isInstanceOf(CommonResult.class);
        assertThat((CommonResult<?>) value)
                .extracting(CommonResult::getCode, CommonResult::getMsg, CommonResult::getData)
                .containsExactly(0, "ok", "cached");
    }

    @Test
    void deserializesLegacyBootTypeIdAsCurrentRobotPlatformClass() {
        RedisSerializer<?> serializer = RobotPlatformRedisAutoConfiguration.buildRedisSerializer();
        byte[] legacyPayload = ("{\"@class\":\"" + LEGACY_BOOT_PREFIX + "framework.common.pojo.CommonResult\","
                + "\"code\":0,\"msg\":\"ok\",\"data\":\"cached\"}").getBytes(StandardCharsets.UTF_8);

        Object value = serializer.deserialize(legacyPayload);

        assertThat(value).isInstanceOf(CommonResult.class);
    }

    @Test
    void rejectsUnavailableLegacyTypeIdWithoutFallingBackToAnArbitraryType() {
        RedisSerializer<?> serializer = RobotPlatformRedisAutoConfiguration.buildRedisSerializer();
        byte[] legacyPayload = ("{\"@class\":\"" + LEGACY_PRIMARY_PREFIX + "framework.common.pojo.NoLongerAvailable\"}")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(legacyPayload))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Unsupported legacy Redis type");
    }

    @Test
    void rejectsNonLegacyTypeIdWithoutRewritingIt() {
        RedisSerializer<?> serializer = RobotPlatformRedisAutoConfiguration.buildRedisSerializer();
        byte[] nonLegacyPayload = "{\"@class\":\"com.example.UntrustedCachedType\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(nonLegacyPayload))
                .isInstanceOf(SerializationException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .doesNotContain("Unsupported legacy Redis type"));
    }
}
