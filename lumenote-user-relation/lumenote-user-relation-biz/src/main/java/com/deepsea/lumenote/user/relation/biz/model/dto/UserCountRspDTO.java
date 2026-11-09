package com.deepsea.lumenote.user.relation.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户计数响应（计数服务返回）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCountRspDTO {

    private Long userId;

    private Long fansTotal;

    private Long followingTotal;

    private Long noteTotal;

    private Long likeTotal;

    private Long collectTotal;
}
