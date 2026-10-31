package com.deepsea.lumenote.user.biz.runner;

import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.user.biz.constants.RedisKeyConstants;
import com.deepsea.lumenote.user.biz.domain.dataobject.PermissionDO;
import com.deepsea.lumenote.user.biz.domain.dataobject.RoleDO;
import com.deepsea.lumenote.user.biz.domain.dataobject.RolePermissionRelDO;
import com.deepsea.lumenote.user.biz.domain.mapper.PermissionDOMapper;
import com.deepsea.lumenote.user.biz.domain.mapper.RoleDOMapper;
import com.deepsea.lumenote.user.biz.domain.mapper.RolePermissionRelDOMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 推送角色权限数据到 Redis 中
 */
@Component
@Slf4j
public class PushRolePermissions2RedisRunner implements ApplicationRunner {

    @Autowired
    private RoleDOMapper roleDOMapper;
    @Autowired
    private RolePermissionRelDOMapper rolePermissionRelDOMapper;
    @Autowired
    private PermissionDOMapper permissionDOMapper;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 权限同步标记 Key
    private static final String PUSH_PERMISSION_FLAG = "push.permission.flag";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("==> 服务启动，开始同步角色权限数据到 Redis 中...");
        try {
            // 是否能够同步数据：原子操作，只有在键 PUSH_PERMISSION_FLAG 不存在时才设置键的值为1，1天后过期，并返回 true；否则返回 false
            boolean canPushed = redisTemplate.opsForValue().setIfAbsent(PUSH_PERMISSION_FLAG, "1", 1, TimeUnit.DAYS);

            // 如果无法同步权限数据
            if (!canPushed) {
                log.warn("==> 角色权限数据已经同步至 Redis 中，不再同步...");
                return;
            }

            // 1.查询出所有的角色数据
            List<RoleDO> roleDOS = roleDOMapper.selectEnabledList();

            // 2.拿到角色的 ID 集合以及角色 Id - Key Map
            List<Long> roleIds = roleDOS.stream().map(RoleDO::getId).toList();
            Map<Long, String> roleIdKeyMap = roleDOS.stream().collect(Collectors.toMap(RoleDO::getId, RoleDO::getRoleKey));

            // 3.根据 ID 集合去查询角色对应的权限数据
            List<RolePermissionRelDO> rolePermissionRelDOS = rolePermissionRelDOMapper.selectByRoleIds(roleIds);

            // 4.查询所有已启用的权限数据
            List<PermissionDO> permissionDOS = permissionDOMapper.selectAppEnabledList();

            // 5.获取权限 ID 集合
            List<Long> permissionIds = permissionDOS.stream().map(PermissionDO::getId).toList();

            // 6.过滤掉被禁用的权限数据，根据角色 ID 分组，并存到 Map 中
            Map<Long, List<RolePermissionRelDO>> rolePermissionsMap = rolePermissionRelDOS.stream()
                    .filter(rel -> permissionIds.contains(rel.getPermissionId()))
                    .collect(Collectors.groupingBy(RolePermissionRelDO::getRoleId));

            // 7.Map<Long, List<RolePermissionRelDO>> 转 Map<Long, List<PermissionDO>>
            Map<Long, PermissionDO> permissionMap = permissionDOS.stream().collect(Collectors.toMap(PermissionDO::getId, p -> p));
            rolePermissionsMap.forEach((roleId, rels) -> {
                List<PermissionDO> permissions = rels.stream().map(rel -> permissionMap.get(rel.getPermissionId())).toList();
                List<String> keys = permissions.stream().map(PermissionDO::getPermissionKey).toList();
                String roleKey = roleIdKeyMap.get(roleId);
                // 8.存到 Redis 中
                for (PermissionDO permission : permissions) {
                    redisTemplate.opsForValue().set(RedisKeyConstants.buildRolePermissionsKey(roleKey), JsonUtils.toJsonString(keys));
                }
            });

            log.info("==> 服务启动，成功同步角色权限数据到 Redis 中...");
        } catch (Exception e) {
            log.error("==> 同步角色权限数据到 Redis 中失败:", e);
        }
    }
}
