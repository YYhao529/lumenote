package com.deepsea.lumenote.count.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询用户计数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindUserCountReqVO {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;
}
