package com.deepsea.lumenote.user.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按用户 ID 校验密码（修改密码前校验原密码）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VerifyPasswordByUserIdReqDTO {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @NotBlank(message = "密码不能为空")
    private String password;
}
