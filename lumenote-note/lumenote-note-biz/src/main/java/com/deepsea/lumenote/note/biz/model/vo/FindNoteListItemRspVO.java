package com.deepsea.lumenote.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记列表条目（个人主页：发布 / 收藏 / 点赞）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindNoteListItemRspVO {

    private Long noteId;

    /**
     * 笔记类型：0 图文，1 视频
     */
    private Integer type;

    private String title;

    /**
     * 封面（第一张图）
     */
    private String cover;

    private String videoUri;

    /**
     * 是否可见：0 公开，1 仅自己可见
     */
    private Integer visible;

    /**
     * 是否置顶
     */
    private Boolean isTop;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 收藏 / 点赞时间（发布列表为空）
     */
    private LocalDateTime actionTime;

    private Long creatorId;

    private String creatorName;

    private String avatar;

    /**
     * 点赞总数
     */
    private Long likeTotal;

    /**
     * 收藏总数
     */
    private Long collectTotal;
}
