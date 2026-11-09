package com.deepsea.framework.jackson.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.YearMonthDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.YearMonthSerializer;
import com.deepsea.framework.common.constant.DateConstants;
import com.deepsea.framework.common.util.JsonUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.TimeZone;

/**
 * Jackson 自动配置类
 */
@AutoConfiguration
public class JacksonAutoConfiguration {

    @Bean
    public ObjectMapper objectMapper(){
        // 初始化一个 ObjectMapper 对象，用来自定义 Jackson 的行为
        ObjectMapper objectMapper = new ObjectMapper();

        // 忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // 设置时区
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // JavaTimeModule 用于指定序列化和反序列化规则
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 支持 LocalDateTime、LocalDate、LocalTime、YearMonth
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateConstants.DATE_FORMAT_Y_M_D_H_M_S));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateConstants.DATE_FORMAT_Y_M_D_H_M_S));

        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateConstants.DATE_FORMAT_Y_M_D));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateConstants.DATE_FORMAT_Y_M_D));

        javaTimeModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(DateConstants.DATE_FORMAT_H_M_S));
        javaTimeModule.addDeserializer(LocalTime.class,
                new LocalTimeDeserializer(DateConstants.DATE_FORMAT_H_M_S));

        javaTimeModule.addSerializer(YearMonth.class,
                new YearMonthSerializer(DateConstants.DATE_FORMAT_Y_M));
        javaTimeModule.addDeserializer(YearMonth.class,
                new YearMonthDeserializer(DateConstants.DATE_FORMAT_Y_M));

        objectMapper.registerModule(javaTimeModule);

        // 雪花 ID 是 19 位 Long，超出 JS Number 安全整数范围（2^53），
        // 默认按 JSON 数字返回会让前端 JSON.parse 丢精度（末尾几位变 0），
        // 典型症状：列表里拿到的 noteId 拿去查详情，报「笔记不存在」。
        // 统一把 Long 序列化成字符串；反序列化仍兼容数字与字符串。
        SimpleModule longToStringModule = new SimpleModule();
        longToStringModule.addSerializer(Long.class, ToStringSerializer.instance);
        longToStringModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(longToStringModule);

        // 初始化 JsonUtils 中的 ObjectMapper
        JsonUtils.init(objectMapper);

        return objectMapper;
    }
}
