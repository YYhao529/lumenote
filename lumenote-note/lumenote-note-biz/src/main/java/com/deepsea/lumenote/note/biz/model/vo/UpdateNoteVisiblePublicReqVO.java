package com.deepsea.lumenote.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记设为公开可见
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateNoteVisiblePublicReqVO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long id;

}
