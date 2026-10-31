package com.deepsea.lumenote.user.relation.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询关注列表响应参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindFollowingUserRspVO {

    private Long userId;

    private String avatar;

    private String nickname;

    private String introduction;
}
