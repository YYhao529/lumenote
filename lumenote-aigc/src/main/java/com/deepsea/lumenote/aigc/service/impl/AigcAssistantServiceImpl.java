package com.deepsea.lumenote.aigc.service.impl;

import com.deepsea.framework.common.exception.BizException;
import com.deepsea.lumenote.aigc.constants.RedisKeyConstants;
import com.deepsea.lumenote.aigc.enums.ResponseCodeEnum;
import com.deepsea.lumenote.aigc.model.dto.ChatReqDTO;
import com.deepsea.lumenote.aigc.model.vo.ChatRspVO;
import com.deepsea.lumenote.aigc.service.AigcAssistantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能助手服务实现
 *
 * <p>会话记忆存在 Redis 里（按 用户 + 会话 维度），只保留最近 N 轮，避免上下文无限增长。</p>
 */
@Service
@Slf4j
public class AigcAssistantServiceImpl implements AigcAssistantService {

    private static final String DEFAULT_SESSION_ID = "default";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    /**
     * 未创建出 ChatClient（通常是没配 API Key）时为 null
     */
    @Autowired(required = false)
    private ChatClient chatClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.ai.deepseek.api-key:}")
    private String apiKey;

    /**
     * 记住最近多少轮对话（1 轮 = 用户一句 + 助手一句）
     */
    @Value("${aigc.history-turns:8}")
    private int historyTurns;

    /**
     * 会话记忆过期时间（秒）
     */
    @Value("${aigc.history-ttl:3600}")
    private long historyTtl;

    /**
     * 每人每分钟最多提问次数
     */
    @Value("${aigc.rate-limit-per-minute:20}")
    private int rateLimitPerMinute;

    @Override
    public ChatRspVO chat(ChatReqDTO reqDTO, Long userId) {
        String sessionId = normalizeSessionId(reqDTO.getSessionId());
        prepare(userId);

        List<Message> messages = buildMessages(userId, sessionId, reqDTO.getMessage());

        ChatResponse response = chatClient.prompt(new Prompt(messages)).call().chatResponse();
        String reply = response == null || response.getResult() == null
                ? ""
                : response.getResult().getOutput().getText();

        if (!StringUtils.hasText(reply)) {
            throw new BizException(ResponseCodeEnum.UPSTREAM_ERROR);
        }

        appendHistory(userId, sessionId, reqDTO.getMessage(), reply);
        return ChatRspVO.builder().sessionId(sessionId).reply(reply).build();
    }

    @Override
    public Flux<String> stream(ChatReqDTO reqDTO, Long userId) {
        String sessionId = normalizeSessionId(reqDTO.getSessionId());
        prepare(userId);

        List<Message> messages = buildMessages(userId, sessionId, reqDTO.getMessage());

        StringBuilder answer = new StringBuilder();

        return chatClient.prompt(new Prompt(messages))
                .stream()
                .content()
                .doOnNext(answer::append)
                .doOnComplete(() -> {
                    if (answer.length() > 0) {
                        appendHistory(userId, sessionId, reqDTO.getMessage(), answer.toString());
                    }
                })
                .doOnError(e -> log.warn("流式对话失败, userId: {}, error: {}", userId, e.getMessage()));
    }

    @Override
    public void clear(Long userId, String sessionId) {
        redisTemplate.delete(RedisKeyConstants.buildChatHistoryKey(
                userId == null ? 0L : userId, normalizeSessionId(sessionId)));
    }

    /**
     * 公共前置检查：模型是否可用 + 限流
     */
    @SuppressWarnings("ConstantConditions")
    private void prepare(Long userId) {
        if (chatClient == null || !StringUtils.hasText(apiKey) || apiKey.startsWith("sk-not-configured")) {
            throw new BizException(ResponseCodeEnum.API_KEY_MISSING);
        }
        if (rateLimitPerMinute > 0 && userId != null) {
            String key = RedisKeyConstants.buildRateLimitKey(userId);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > rateLimitPerMinute) {
                throw new BizException(ResponseCodeEnum.RATE_LIMITED);
            }
        }
    }

    /**
     * 组装上下文：历史消息 + 本轮提问
     */
    private List<Message> buildMessages(Long userId, String sessionId, String question) {
        List<Message> messages = new ArrayList<>();
        for (Map<String, String> item : loadHistory(userId, sessionId)) {
            String content = item.get("content");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            messages.add(ROLE_USER.equals(item.get("role"))
                    ? new UserMessage(content)
                    : new AssistantMessage(content));
        }
        messages.add(new UserMessage(question));
        return messages;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> loadHistory(Long userId, String sessionId) {
        try {
            Object raw = redisTemplate.opsForValue()
                    .get(RedisKeyConstants.buildChatHistoryKey(userId == null ? 0L : userId, sessionId));
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, String>> history = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("role", String.valueOf(map.get("role")));
                    entry.put("content", String.valueOf(map.get("content")));
                    history.add(entry);
                }
            }
            return history;
        } catch (Exception e) {
            log.warn("读取会话历史失败，按空历史处理: {}", e.getMessage());
            return List.of();
        }
    }

    private void appendHistory(Long userId, String sessionId, String question, String reply) {
        try {
            String key = RedisKeyConstants.buildChatHistoryKey(userId == null ? 0L : userId, sessionId);
            List<Map<String, String>> history = new ArrayList<>(loadHistory(userId, sessionId));

            history.add(Map.of("role", ROLE_USER, "content", question));
            history.add(Map.of("role", ROLE_ASSISTANT, "content", reply));

            // 只保留最近 N 轮
            int maxSize = Math.max(1, historyTurns) * 2;
            if (history.size() > maxSize) {
                history = new ArrayList<>(history.subList(history.size() - maxSize, history.size()));
            }

            redisTemplate.opsForValue().set(key, history, Duration.ofSeconds(historyTtl));
        } catch (Exception e) {
            log.warn("保存会话历史失败: {}", e.getMessage());
        }
    }

    private String normalizeSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId.trim() : DEFAULT_SESSION_ID;
    }
}
