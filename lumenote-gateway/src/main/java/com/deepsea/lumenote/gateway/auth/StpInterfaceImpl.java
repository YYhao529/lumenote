package com.deepsea.lumenote.gateway.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepsea.lumenote.gateway.constants.RedisKeyConstants;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 自定义权限验证接口扩展
 */
@Component
@Slf4j
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取用户权限
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 返回此 loginId 拥有的权限列表
        log.info("## 获取用户权限列表, loginId: {}", loginId);

        return Collections.emptyList();
    }

    /**
     * 获取用户角色
     */
    @Override
    @SneakyThrows
    public List<String> getRoleList(Object loginId, String loginType) {
        // 返回此 loginId 拥有的角色列表
        log.info("## 获取用户角色列表, loginId: {}", loginId);

        // 查询该用户所属的角色
        String userRoleKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
        String rolesStr = redisTemplate.opsForValue().get(userRoleKey);

        if (StringUtils.isBlank(rolesStr)) {
            return null;
        }


        // 将 JSON 字符串转换为 List<String> 集合
        String jsonStr = objectMapper.readValue(rolesStr, String.class);
        return objectMapper.readValue(jsonStr, new TypeReference<>(){});
    }
}
