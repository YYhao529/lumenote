package com.deepsea.lumenote.aigc.controller;

import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.biz.operationlog.aspect.ApiOperationLog;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.aigc.model.dto.ChatReqDTO;
import com.deepsea.lumenote.aigc.model.dto.ClearChatReqDTO;
import com.deepsea.lumenote.aigc.model.vo.ChatRspVO;
import com.deepsea.lumenote.aigc.service.AigcAssistantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;

/**
 * 智能助手
 */
@RestController
@RequestMapping("/aigc")
@Slf4j
public class AigcController {

    /**
     * 流式响应超时时间（毫秒）
     */
    private static final long SSE_TIMEOUT = 180_000L;

    @Autowired
    private AigcAssistantService aigcAssistantService;

    @PostMapping("/chat")
    @ApiOperationLog(description = "智能助手对话")
    public Response<ChatRspVO> chat(@RequestBody @Validated ChatReqDTO chatReqDTO) {
        return Response.success(aigcAssistantService.chat(chatReqDTO, LoginUserContextHolder.getUserId()));
    }

    /**
     * 流式对话：返回 SSE，前端按 token 渲染打字机效果
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody @Validated ChatReqDTO chatReqDTO) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Long userId = LoginUserContextHolder.getUserId();

        try {
            Disposable disposable = aigcAssistantService.stream(chatReqDTO, userId).subscribe(
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (IOException e) {
                            log.debug("客户端已断开，停止推送: {}", e.getMessage());
                        }
                    },
                    error -> {
                        sendError(emitter, error);
                        emitter.complete();
                    },
                    emitter::complete
            );
            emitter.onCompletion(disposable::dispose);
            emitter.onTimeout(disposable::dispose);
            emitter.onError(e -> disposable.dispose());
        } catch (Exception e) {
            sendError(emitter, e);
            emitter.complete();
        }

        return emitter;
    }

    @PostMapping("/chat/clear")
    @ApiOperationLog(description = "清空智能助手会话")
    public Response<?> clear(@RequestBody(required = false) ClearChatReqDTO clearChatReqDTO) {
        String sessionId = clearChatReqDTO == null ? null : clearChatReqDTO.getSessionId();
        aigcAssistantService.clear(LoginUserContextHolder.getUserId(), sessionId);
        return Response.success();
    }

    private void sendError(SseEmitter emitter, Throwable error) {
        String message = error instanceof BizException biz
                ? biz.getErrorMessage()
                : "AI 服务暂时不可用，请稍后再试";
        log.warn("智能助手流式请求失败: {}", error.getMessage());
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException e) {
            log.debug("错误事件推送失败: {}", e.getMessage());
        }
    }
}
