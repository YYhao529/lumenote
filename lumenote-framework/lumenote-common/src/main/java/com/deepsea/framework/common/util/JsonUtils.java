package com.deepsea.framework.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * JSON 工具类
 */
public class JsonUtils {

    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        OBJECT_MAPPER.registerModules(new JavaTimeModule());    // 解决 LocalDateTime 的序列化问题
    }

    /**
     * 初始化：统一使用 Spring Boot 个性化配置的 ObjectMapper
     */
    public static void init(ObjectMapper objectMapper) {
        OBJECT_MAPPER = objectMapper;
    }

    /**
     * 将对象转换为 JSON 字符串
     */
    @SneakyThrows
    public static String toJsonString(Object obj) {
        return OBJECT_MAPPER.writeValueAsString(obj);
    }

    /**
     * 将 JSON 字符串转换为对象
     */
    @SneakyThrows
    public static <T> T parseObject(String jsonStr, Class<T> clazz) {
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }

        return OBJECT_MAPPER.readValue(jsonStr, clazz);
    }

    /**
     * 将 JSON 字符串转换为 Map
     */
    @SneakyThrows
    public static <K, V> Map<K, V> parseMap(String jsonStr, Class<K> keyType, Class<V> valueType) {
        MapType mapType = OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, keyType, valueType);

        // 将 JSON 字符串转换为 Map
        return OBJECT_MAPPER.readValue(jsonStr, mapType);
    }

    /**
     *
     * 将 JSON 字符串解析为指定类型的 List 对象
     */
    public static <T> List<T> parseList(String jsonStr, Class<T> clazz) throws Exception {
        JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz);

        // 将 JSON 字符串解析为 List
        return OBJECT_MAPPER.readValue(jsonStr, javaType);
    }

}
