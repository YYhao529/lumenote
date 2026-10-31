package com.deepsea.lumenote.search.model.vo;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询热门笔记（发现页推荐流）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindHotNoteReqVO {

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1; // 默认值为第一页
}
