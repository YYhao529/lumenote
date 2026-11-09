package com.deepsea.lumenote.user.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 根据手机号查询用户信息
 *
 * 注意：这里只返回用户 ID，绝不返回密码密文。
 * 密码校验请走 /user/password/verify 接口。
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindUserByPhoneRspDTO {

    private Long id;

}
