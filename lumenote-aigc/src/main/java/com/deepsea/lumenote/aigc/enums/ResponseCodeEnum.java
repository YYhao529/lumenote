package com.deepsea.lumenote.aigc.enums;

import com.deepsea.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AIGC 模块响应码
 */
@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("AIGC-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("AIGC-10001", "参数错误"),

    // ----------- 业务异常状态码 -----------
    API_KEY_MISSING("AIGC-20001", "智能助手还没配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY 后重启服务"),
    UPSTREAM_ERROR("AIGC-20002", "AI 服务暂时不可用，请稍后再试"),
    MESSAGE_TOO_LONG("AIGC-20003", "单条消息太长了，请控制在 2000 字以内"),
    RATE_LIMITED("AIGC-20004", "问得太快啦，歇一会儿再试"),
    ;

    private final String errorCode;
    private final String errorMessage;
}
