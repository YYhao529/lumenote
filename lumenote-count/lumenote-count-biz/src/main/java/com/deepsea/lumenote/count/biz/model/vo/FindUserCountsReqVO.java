package com.deepsea.lumenote.count.biz.model.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量查询用户计数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindUserCountsReqVO {

    @NotEmpty(message = "用户 ID 集合不能为空")
    private List<Long> userIds;
}
