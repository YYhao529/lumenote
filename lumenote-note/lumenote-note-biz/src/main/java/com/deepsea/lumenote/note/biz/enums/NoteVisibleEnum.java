package com.deepsea.lumenote.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 笔记可见性
 */
@Getter
@AllArgsConstructor
public enum NoteVisibleEnum {

    PUBLIC(0),  // 公开，对所有人可见
    PRIVATE(1), // 私密，只对自己可见
    ;

    private final Integer code;
}
