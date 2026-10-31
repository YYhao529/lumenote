package com.deepsea.lumenote.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记置顶/取消置顶
 **/
@Data
@NoArgsConstructor
public class TopNoteReqVO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long id;

    @NotNull(message = "置顶状态不能为空")
    private Boolean isTop;

}
