package com.deepsea.lumenote.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 笔记状态
 */
@Getter
@AllArgsConstructor
public enum NoteStatusEnum {

    BE_EXAMINE(0),  // 待审核
    NORMAL(1),  // 正常展示
    DELETED(2),  // 已删除
    DOWNED(3),  // 已下架
    ;

    private final Integer code;
}
