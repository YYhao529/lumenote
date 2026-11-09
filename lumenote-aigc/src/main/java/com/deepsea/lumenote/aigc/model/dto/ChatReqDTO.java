package com.deepsea.lumenote.aigc.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能助手对话请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatReqDTO {

    /**
     * 会话 ID（前端生成，用于多轮上下文；不传则用 default）
     */
    private String sessionId;

    /**
     * 用户提问
     */
    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "单条消息不能超过 2000 字")
    private String message;
}
