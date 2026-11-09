package com.deepsea.lumenote.user.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改密码（必须携带原密码，用户服务内部完成校验与加密）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserPasswordReqDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需为 6-20 位")
    private String newPassword;
}
