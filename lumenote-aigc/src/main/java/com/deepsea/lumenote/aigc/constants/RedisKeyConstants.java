package com.deepsea.lumenote.aigc.constants;

/**
 * Redis Key 常量
 */
public class RedisKeyConstants {

    /**
     * 智能助手会话历史：aigc:chat:history:{userId}:{sessionId}
     */
    private static final String CHAT_HISTORY_KEY_PREFIX = "aigc:chat:history:";

    /**
     * 限流：aigc:chat:rate:{userId}
     */
    private static final String RATE_LIMIT_KEY_PREFIX = "aigc:chat:rate:";

    public static String buildChatHistoryKey(Long userId, String sessionId) {
        return CHAT_HISTORY_KEY_PREFIX + userId + ":" + sessionId;
    }

    public static String buildRateLimitKey(Long userId) {
        return RATE_LIMIT_KEY_PREFIX + userId;
    }
}
