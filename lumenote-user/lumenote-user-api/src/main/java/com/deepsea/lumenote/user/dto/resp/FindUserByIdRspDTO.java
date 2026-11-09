package com.deepsea.lumenote.user.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindUserByIdRspDTO {

    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 拾光笔记号（个人主页展示用）
     */
    private String lumenoteId;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 背景图
     */
    private String backgroundImg;
}
