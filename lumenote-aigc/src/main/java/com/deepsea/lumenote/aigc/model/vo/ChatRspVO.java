package com.deepsea.lumenote.aigc.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能助手对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRspVO {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 助手回复
     */
    private String reply;
}
