package com.deepsea.lumenote.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *  笔记操作
 */
@Getter
@AllArgsConstructor
public enum NoteOperateEnum {
    // 笔记发布
    PUBLISH(1),
    // 笔记删除
    DELETE(0),
    ;

    private final Integer code;

}

