package com.deepsea.lumenote.note.biz.model.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量查询当前用户是否点赞
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindNoteLikedStatusReqVO {

    @NotEmpty(message = "笔记 ID 集合不能为空")
    @Size(max = 50, message = "单次最多查询 50 条笔记")
    private List<Long> noteIds;
}
