package com.deepsea.framework.biz.context.filter;

import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.common.constant.GlobalConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 提取请求头中的用户 ID 保存到上下文中，方便后面使用
 *
 * <p>注意：这个过滤器对<b>每个请求</b>都会执行，日志级别必须是 DEBUG，
 * 否则健康检查、静态资源之类的请求会把日志刷满。排查问题时把
 * {@code com.deepsea.framework.biz.context} 的日志级别调成 DEBUG 即可。</p>
 */
@Slf4j
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 从请求头中获取用户 ID（网关注入，未登录时为空）
        String userId = request.getHeader(GlobalConstants.USER_ID);

        if (StringUtils.isBlank(userId)) {
            if (log.isDebugEnabled()) {
                log.debug("未携带用户 ID，直接放行: {} {}", request.getMethod(), request.getRequestURI());
            }
            chain.doFilter(request, response);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("设置 userId 到上下文, userId: {}, uri: {}", userId, request.getRequestURI());
        }
        LoginUserContextHolder.setUserId(userId);

        try {
            chain.doFilter(request, response);
        } finally {
            // 一定要删除 ThreadLocal，防止线程复用时串号 + 内存泄露
            LoginUserContextHolder.remove();
            if (log.isDebugEnabled()) {
                log.debug("清理 userId 上下文, userId: {}", userId);
            }
        }
    }
}
