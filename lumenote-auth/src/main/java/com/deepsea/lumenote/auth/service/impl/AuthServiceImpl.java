package com.deepsea.lumenote.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.base.Preconditions;
import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.auth.constant.RedisKeyConstants;
import com.deepsea.lumenote.auth.enums.LoginTypeEnum;
import com.deepsea.lumenote.auth.enums.ResponseCodeEnum;
import com.deepsea.lumenote.auth.model.vo.user.UpdatePasswordReqVO;
import com.deepsea.lumenote.auth.model.vo.user.UserLoginReqVO;
import com.deepsea.lumenote.auth.rpc.UserRpcService;
import com.deepsea.lumenote.auth.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private UserRpcService userRpcService;

    /**
     * 登录与注册
     */
    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        Integer type = userLoginReqVO.getType();

        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);

        // 登录类型错误
        if (Objects.isNull(loginTypeEnum)) {
            throw new BizException(ResponseCodeEnum.LOGIN_TYPE_ERROR);
        }

        Long userId = null;
        // 判断登陆类型
        switch (loginTypeEnum) {
            case VERIFICATION_CODE: // 验证码登录
                String verificationCode = userLoginReqVO.getCode();

                // 校验入参验证码是否为空
                Preconditions.checkArgument(StrUtil.isNotBlank(verificationCode), "验证码不能为空");

                // 构建验证码 Redis Key
                String key = RedisKeyConstants.buildVerificationCodeKey(phone);
                String attemptsKey = RedisKeyConstants.buildVerificationCodeAttemptsKey(phone);
                // 查询存储在 Redis 中该用户的登录验证码
                String sentCode = (String) redisTemplate.opsForValue().get(key);

                if (StringUtils.isBlank(sentCode)) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }

                // 判断用户提交的验证码与 Redis 中的验证码是否一致
                if (!StringUtils.equals(verificationCode, sentCode)) {
                    // 记录错误次数，10 分钟内同一手机号最多错 5 次
                    Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
                    if (Objects.nonNull(attempts) && attempts == 1L) {
                        redisTemplate.expire(attemptsKey, 10, TimeUnit.MINUTES);
                    }
                    if (Objects.nonNull(attempts) && attempts >= 5L) {
                        // 错误次数过多：作废当前验证码，强制重新获取，防止暴力枚举
                        redisTemplate.delete(key);
                        throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_TOO_MANY_ATTEMPTS);
                    }
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }

                // RPC: 调用用户服务，注册用户
                Long userIdTmp = userRpcService.registerUser(phone);

                // 若调用用户服务，返回的用户 ID 为空，则提示登录失败
                if (Objects.isNull(userIdTmp)) {
                    throw new BizException(ResponseCodeEnum.LOGIN_FAIL);
                }

                // 验证码一次性使用：注册成功后再删除，避免下游失败导致用户必须重新获取验证码
                redisTemplate.delete(key);
                redisTemplate.delete(attemptsKey);

                userId = userIdTmp;
                break;
            case PASSWORD:  // 密码登录
                String password = userLoginReqVO.getPassword();

                if (StringUtils.isBlank(password)) {
                    throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }

                // RPC: 用户服务内部完成密码校验，密码密文不再出服务
                Long userIdByPassword = userRpcService.verifyPassword(phone, password);

                if (Objects.isNull(userIdByPassword)) {
                    throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }

                userId = userIdByPassword;
                break;
            default:
                break;
        }

        // SaToken 登录用户，入参为用户 ID
        StpUtil.login(userId);

        // 获取 Token 令牌
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        // 返回 token 令牌
        return Response.success(tokenInfo.tokenValue);
    }

    /**
     * 退出登录
     */
    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();

        log.info("==> 用户退出登录, userId: {}", userId);

        // 退出登录(指定用户 ID)
        StpUtil.logout(userId);

        return Response.success();
    }

    /**
     * 修改密码
     */
    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        Long userId = LoginUserContextHolder.getUserId();
        if (Objects.isNull(userId)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        // 原密码校验、新密码加密都在用户服务内部完成；这里只负责让 token 失效
        boolean updated = userRpcService.updatePassword(
                updatePasswordReqVO.getOldPassword(), updatePasswordReqVO.getNewPassword());
        if (!updated) {
            throw new BizException(ResponseCodeEnum.OLD_PASSWORD_ERROR);
        }

        // 改密后让该用户所有已签发的 token 失效，强制重新登录
        StpUtil.logout(userId);

        return Response.success();
    }

}
