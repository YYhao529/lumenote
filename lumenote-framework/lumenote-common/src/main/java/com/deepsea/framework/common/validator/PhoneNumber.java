package com.deepsea.framework.common.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定义手机校验注解
 */
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
public @interface PhoneNumber {

    // 校验失败时，默认的错误提示信息
    String message() default "手机号格式不正确, 需为 11 位数字";

    // 校验分组：可以实现不同场景用不同校验规则，比如新增和更新校验不一样
    Class<?>[] groups() default {};

    // 负载信息：可以给这个注解带额外的自定义数据，比如错误级别
    Class<? extends Payload>[] payload() default {};
}
