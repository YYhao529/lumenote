package com.deepsea.lumenote.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 删除笔记请求 VO
 */
@Data
@NoArgsConstructor
public class DeleteNoteReqVO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long noteId;
}
