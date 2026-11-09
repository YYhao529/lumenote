package com.deepsea.framework.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参数条件工具
 */
public final class ParamUtils {
    private ParamUtils(){
    }

    // ============================== 校验昵称 ==============================
    // 定义昵称长度范围
    private static final int NICK_NAME_MIN_LENGTH = 2;
    private static final int NICK_NAME_MAX_LENGTH = 24;

    // 定义特殊字符的正则表达式
    private static final String NICK_NAME_REGEX = "[!@#$%^&*(),.?\":{}|<>]";

    /**
     * 昵称校验
     */
    public static boolean checkNickname(String nickname){
        if (nickname == null) {
            return false;
        }
        // 检查长度
        if (nickname.length()<NICK_NAME_MIN_LENGTH || nickname.length()>NICK_NAME_MAX_LENGTH){
            return false;
        }
        // 检查是否有特殊字符
        Pattern pattern = Pattern.compile(NICK_NAME_REGEX);
        return !pattern.matcher(nickname).find();
    }

    // ============================== 校验拾光笔记号 ==============================
    // 定义 ID 长度范围
    private static final int ID_MIN_LENGTH = 6;
    private static final int ID_MAX_LENGTH = 15;

    // 定义正则表达式
    private static final String ID_REGEX = "^[a-zA-Z0-9_]+$";

    /**
     * 拾光笔记 ID 校验
     */
    public static boolean checkLumenoteId(String lumenoteId){
        if (lumenoteId == null) {
            return false;
        }
        // 检查长度
        if (lumenoteId.length()<ID_MIN_LENGTH || lumenoteId.length()>ID_MAX_LENGTH){
            return false;
        }
        // 检查格式
        Pattern pattern = Pattern.compile(ID_REGEX);
        return pattern.matcher(lumenoteId).matches();
    }

    /**
     * 字符串长度校验
     */
    public static boolean checkLength(String str,int length){
        // 检查长度
        if (str == null || str.isEmpty() || str.length()>length){
            return false;
        }
        return true;
    }
}
