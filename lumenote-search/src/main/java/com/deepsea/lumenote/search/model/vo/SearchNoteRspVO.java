package com.deepsea.lumenote.search.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 *  搜索笔记
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchNoteRspVO {

    /**
     * 笔记ID
     */
    private Long noteId;

    /**
     * 封面
     */
    private String cover;

    /**
     * 笔记类型：0 图文，1 视频
     */
    private Integer type;

    /**
     * 视频地址（视频笔记用于列表内预览）
     */
    private String videoUri;

    /**
     * 标题
     */
    private String title;

    /**
     * 标题：关键词高亮
     */
    private String highlightTitle;

    /**
     * 发布者头像
     */
    private String avatar;

    /**
     * 发布者昵称
     */
    private String nickname;

    /**
     * 最后一次编辑时间
     */
    private String updateTime;

    /**
     * 被点赞总数
     */
    private String likeTotal;

    /**
     * 被评论数
     */
    private String commentTotal;

    /**
     * 被收藏数
     */
    private String collectTotal;

}

