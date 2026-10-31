package com.deepsea.lumenote.auth.constant;

public class RedisKeyConstants {

    /**
     * 验证码 key 前缀
     */
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    /**
     * 构建验证码 key
     */
    public static String buildVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + phone;
    }
}
