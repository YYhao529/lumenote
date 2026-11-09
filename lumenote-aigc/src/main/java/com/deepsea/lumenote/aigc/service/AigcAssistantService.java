package com.deepsea.lumenote.aigc.service;

import com.deepsea.lumenote.aigc.model.dto.ChatReqDTO;
import com.deepsea.lumenote.aigc.model.vo.ChatRspVO;
import reactor.core.publisher.Flux;

/**
 * 智能助手服务
 */
public interface AigcAssistantService {

    /**
     * 一次性对话（等模型全部生成完再返回）
     */
    ChatRspVO chat(ChatReqDTO reqDTO, Long userId);

    /**
     * 流式对话（逐字返回，前端做打字机效果）
     */
    Flux<String> stream(ChatReqDTO reqDTO, Long userId);

    /**
     * 清空会话记忆
     */
    void clear(Long userId, String sessionId);
}
