package com.deepsea.lumenote.user.biz.constants;

public class RedisKeyConstants {

    /**
     * 时光笔记全局 ID 生成器
     */
    public static final String LUMENOTE_ID_GENERATOR_KEY = "lumenote.id.generator";

    /**
     * 用户角色数据 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permission:";

    /**
     * 用户信息数据 KEY 前缀
     */
    private static final String USER_INFO_KEY_PREFIX = "user:info:";

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

    /**
     * 构建用户信息 KEY
     */
    public static String buildUserInfoKey(Long userId) {
        return USER_INFO_KEY_PREFIX + userId;
    }
}
