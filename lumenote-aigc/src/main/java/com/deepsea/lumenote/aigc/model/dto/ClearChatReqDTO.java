package com.deepsea.lumenote.aigc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 清空会话请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClearChatReqDTO {

    /**
     * 会话 ID
     */
    private String sessionId;
}
