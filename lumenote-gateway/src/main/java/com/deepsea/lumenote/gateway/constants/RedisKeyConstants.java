package com.deepsea.lumenote.gateway.constants;

public class RedisKeyConstants {

    /**
     * 用户角色数据 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permission:";

    /**
     * 构建用户-角色 KEY
     */
    public static String buildUserRoleKey(Long userId) {
        return USER_ROLES_KEY_PREFIX + userId;
    }

    /**
     * 构建角色对应的权限集合 KEY
     */
    public static String buildRolePermissionsKey(String roleKey) {
        return ROLE_PERMISSIONS_KEY_PREFIX + roleKey;
    }
}
