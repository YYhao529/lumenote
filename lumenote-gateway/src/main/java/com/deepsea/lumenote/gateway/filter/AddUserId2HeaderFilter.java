package com.deepsea.lumenote.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.deepsea.framework.common.constant.GlobalConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 转发请求时，将用户 ID 添加到 Header 请求头中，传给下游服务
 */
@Component
@Slf4j
public class AddUserId2HeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("==================> TokenConvertFilter");

        // 用户 ID
        Long userId = null;
        try {
            // 已登录，获取用户id
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            // 若没有登录，则直接放行
            return chain.filter(exchange);
        }

        log.info("## 当前登录的用户 ID: {}", userId);

        // 将用户 id 添加到请求头中
        Long finalUserId = userId;
        ServerWebExchange newExchange = exchange.mutate()
                .request(builder -> builder.header(GlobalConstants.USER_ID, String.valueOf(finalUserId)))
                .build();
        return chain.filter(newExchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
