package com.deepsea.lumenote.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.auth.constant.RedisKeyConstants;
import com.deepsea.lumenote.auth.enums.ResponseCodeEnum;
import com.deepsea.lumenote.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.deepsea.lumenote.auth.service.VerificationCodeService;
import com.deepsea.lumenote.auth.sms.AliyunSmsHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    @Autowired
    private AliyunSmsHelper aliyunSmsHelper;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 发送短信验证码
     */
    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        // 拿到手机号
        String phone = sendVerificationCodeReqVO.getPhone();

        // 构造该手机号在 redis 中的 key
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);

        // 判断是否已发送验证码
        if (redisTemplate.hasKey(key)){
            // 若之前发送的验证码未过期，则提示发送频繁
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        // 生成 6 位随机数字验证码
        String verificationCode = RandomUtil.randomNumbers(6);

        log.info("==> 手机号: {}, 已生成验证码：【{}】", phone, verificationCode);

        // 调用第三方短信发送服务
        threadPoolTaskExecutor.submit(()->{
            aliyunSmsHelper.sendMessage(phone, verificationCode, 5);
        });

        // 存储验证码到 redis，并设置过期时间为 3 分钟
        redisTemplate.opsForValue().set(key, verificationCode,3, TimeUnit.MINUTES);

        return Response.success();
    }
}
