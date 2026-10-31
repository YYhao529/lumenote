package com.deepsea.lumenote.search.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索用户
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchUserRspVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 昵称：关键词高亮
     */
    private String highlightNickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 时光笔记ID
     */
    private String lumenoteId;

    /**
     * 笔记发布总数
     */
    private Integer noteTotal;

    /**
     * 粉丝总数
     */
    private String fansTotal;

}