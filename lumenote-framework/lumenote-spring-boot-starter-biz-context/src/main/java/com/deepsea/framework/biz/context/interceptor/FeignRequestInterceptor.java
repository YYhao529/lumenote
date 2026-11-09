package com.deepsea.framework.biz.context.interceptor;

import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.common.constant.GlobalConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Feign 请求拦截器
 */
@Slf4j
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        // 获取登录用户 ID
        Long userId = LoginUserContextHolder.getUserId();
        if (Objects.isNull(userId)){
            // 如果为空，直接跳过
            return;
        }
        // 如果不为空则放入请求头中，传递给下游微服务
        requestTemplate.header(GlobalConstants.USER_ID, userId.toString());
        // 每次 Feign 调用都会走到这里，INFO 会刷屏，只在 DEBUG 下记录
        if (log.isDebugEnabled()) {
            log.debug("feign 透传 userId: {}, target: {}", userId, requestTemplate.feignTarget().name());
        }
    }
}
