package com.deepsea.lumenote.count.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户计数响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCountRspVO {

    private Long userId;

    /**
     * 粉丝总数
     */
    private Long fansTotal;

    /**
     * 关注总数
     */
    private Long followingTotal;

    /**
     * 笔记发布总数
     */
    private Long noteTotal;

    /**
     * 笔记被点赞总数
     */
    private Long likeTotal;

    /**
     * 笔记被收藏总数
     */
    private Long collectTotal;
}
