package com.deepsea.lumenote.auth.model.vo.user;

import com.deepsea.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录（支持账号密码和手机验证码两种方式）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserLoginReqVO {

    /**
     * 手机号
     */
    @PhoneNumber
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 验证码
     */
    private String code;

    /**
     * 密码
     */
    private String password;

    /**
     * 登录类型：验证码，或者是账号密码
     */
    @NotNull(message = "登录类型不能为空")
    private Integer type;
}
