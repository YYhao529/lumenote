package com.deepsea.lumenote.count.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记计数响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteCountRspVO {

    private Long noteId;

    /**
     * 点赞总数
     */
    private Long likeTotal;

    /**
     * 收藏总数
     */
    private Long collectTotal;

    /**
     * 评论总数
     */
    private Long commentTotal;
}
