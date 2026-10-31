package com.deepsea.lumenote.count.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记计数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteCountDO {
    private Long id;

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