package com.robot.platform.framework.redis.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * Reads cache entries written before the namespace migration.
 */
final class LegacyTypeIdRedisSerializer implements RedisSerializer<Object> {

    private static final String TYPE_ID_PROPERTY = "@class";
    private static final String LEGACY_PRIMARY_PREFIX = "cn.iocoder." + "yu" + "dao.";
    private static final String LEGACY_BOOT_PREFIX = "cn.iocoder." + "boot.";
    private static final String ROBOT_PLATFORM_PREFIX = "com.robot.platform.";

    private final RedisSerializer<Object> delegate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    LegacyTypeIdRedisSerializer(RedisSerializer<Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        return delegate.serialize(value);
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        return delegate.deserialize(rewriteLegacyTypeIds(bytes));
    }

    private byte[] rewriteLegacyTypeIds(byte[] bytes) {
        if (bytes == null || !containsLegacyPrefix(bytes)) {
            return bytes;
        }
        try {
            JsonNode root = objectMapper.readTree(bytes);
            rewriteLegacyTypeIds(root);
            return objectMapper.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new SerializationException("Could not rewrite legacy Redis type identifiers", e);
        }
    }

    private void rewriteLegacyTypeIds(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            JsonNode typeId = objectNode.get(TYPE_ID_PROPERTY);
            if (typeId != null && typeId.isTextual()) {
                String mappedTypeId = mapLegacyTypeId(typeId.textValue());
                if (mappedTypeId != null) {
                    objectNode.put(TYPE_ID_PROPERTY, mappedTypeId);
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                rewriteLegacyTypeIds(fields.next().getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                rewriteLegacyTypeIds(child);
            }
        }
    }

    private String mapLegacyTypeId(String typeId) {
        String targetTypeId;
        if (typeId.startsWith(LEGACY_PRIMARY_PREFIX)) {
            targetTypeId = ROBOT_PLATFORM_PREFIX + typeId.substring(LEGACY_PRIMARY_PREFIX.length());
        } else if (typeId.startsWith(LEGACY_BOOT_PREFIX)) {
            targetTypeId = ROBOT_PLATFORM_PREFIX + typeId.substring(LEGACY_BOOT_PREFIX.length());
        } else {
            return null;
        }
        try {
            Class<?> targetType = Class.forName(targetTypeId);
            if (!targetType.getName().startsWith(ROBOT_PLATFORM_PREFIX)) {
                throw new SerializationException("Legacy Redis type resolves outside Robot Platform: " + typeId);
            }
            return targetTypeId;
        } catch (ClassNotFoundException e) {
            throw new SerializationException("Unsupported legacy Redis type: " + typeId, e);
        }
    }

    private boolean containsLegacyPrefix(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        return content.contains(LEGACY_PRIMARY_PREFIX) || content.contains(LEGACY_BOOT_PREFIX);
    }

}
