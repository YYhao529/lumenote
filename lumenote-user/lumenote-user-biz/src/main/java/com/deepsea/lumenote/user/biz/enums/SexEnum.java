package com.deepsea.lumenote.user.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 性别
 */
@Getter
@AllArgsConstructor
public enum SexEnum {

    WOMAN(0),
    MAN(1);

    private final Integer value;

    public static boolean isValid(Integer value) {
        for (SexEnum sexEnum : SexEnum.values()) {
            if (Objects.equals(sexEnum.getValue(), value)) {
                return true;
            }
        }
        return false;
    }
}
