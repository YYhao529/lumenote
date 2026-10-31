package com.deepsea.lumenote.count.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户计数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCountDO {
    private Long id;

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
     * 笔记总数
     */
    private Long noteTotal;

    /**
     * 点赞总数
     */
    private Long likeTotal;

    /**
     * 收藏总数
     */
    private Long collectTotal;
}