package com.deepsea.lumenote.note.biz.enums;

import com.deepsea.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {
    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("NOTE-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("NOTE-10001", "参数错误"),

    // ----------- 业务异常状态码 -----------
    NOTE_TYPE_ERROR("NOTE-20000", "未知的笔记类型"),
    NOTE_PUBLISH_FAIL("NOTE-20001", "笔记发布失败"),
    NOTE_NOT_FOUND("NOTE-20002", "笔记不存在"),
    NOTE_PRIVATE("NOTE-20003", "作者已将该笔记设置为仅自己可见"),
    NOTE_UPDATE_FAIL("NOTE-20004", "笔记更新失败"),
    TOPIC_NOT_FOUND("NOTE-20005", "话题不存在"),
    NOTE_PERMISSION_DENIED("NOTE-20006", "笔记权限不足"),
    NOTE_CONTENT_SAVE_FAIL("NOTE-20007", "笔记内容保存失败"),
    NOTE_CANT_VISIBLE_ONLY_ME("NOTE-20008", "此笔记无法修改为仅自己可见"),
    NOTE_CANT_TOP("NOTE-20009", "此笔记无法置顶/取消置顶"),
    NOTE_ALREADY_LIKED("NOTE-20010", "您已经点赞过该笔记"),
    NOTE_NOT_LIKED("NOTE-20011", "您未点赞该篇笔记，无法取消点赞"),
    NOTE_ALREADY_COLLECTED("NOTE-20012", "您已经收藏过该篇笔记"),
    NOTE_NOT_COLLECTED("NOTE-20013", "您未收藏该篇笔记，无法取消收藏"),
    NOTE_CANT_VISIBLE_PUBLIC("NOTE-20014", "此笔记无法修改为公开"),
    ;

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}
