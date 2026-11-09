package com.deepsea.lumenote.user.dto.req;

import com.deepsea.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校验手机号 + 密码（仅服务内部调用，返回用户 ID，不返回密码密文）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VerifyPasswordReqDTO {

    @PhoneNumber
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;
}
