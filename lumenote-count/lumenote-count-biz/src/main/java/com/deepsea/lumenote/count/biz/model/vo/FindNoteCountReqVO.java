package com.deepsea.lumenote.count.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询笔记计数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindNoteCountReqVO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long noteId;
}
