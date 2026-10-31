package com.deepsea.lumenote.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询用户的笔记列表（发布 / 收藏 / 点赞）请求参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindUserNoteListReqVO {

    @NotNull(message = "查询用户 ID 不能为空")
    private Long userId;

    @NotNull(message = "页码不能为空")
    private Integer pageNo = 1;  // 默认为第一页
}
