package com.deepsea.lumenote.user.relation.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量查询用户计数请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindUserCountsReqDTO {

    private List<Long> userIds;
}
