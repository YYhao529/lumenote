package com.deepsea.lumenote.auth.sms;

import cn.hutool.core.lang.Validator;
import cn.hutool.json.JSONUtil;
import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.auth.enums.ResponseCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 短信发送工具类
 */
@Slf4j
@Component
public class AliyunSmsHelper {

    @Autowired
    private Client client;

    @Value("${aliyun.sms.sign-name}")
    private String signName;

    @Value("${aliyun.sms.template-code}")
    private String templateCode;

    /**
     * 发送短信验证码
     */
    public boolean sendMessage(String mobile, String code, int validMinute) {
        // 校验手机号格式是否合法
        if (!Validator.isMobile(mobile)) {
            throw new BizException(ResponseCodeEnum.PHONE_NUMBER_FORMAT_ERROR);
        }

        try {
            // 构建短信模板参数（验证码 + 有效时长）
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("code", code);
            paramMap.put("min", String.valueOf(validMinute));
            String templateParam = JSONUtil.toJsonStr(paramMap);

            // 构建发送短信验证码请求
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam(templateParam)
                    .setPhoneNumber(mobile);

            // 调用阿里云 SDK 发送短信
            log.info("==> 开始短信发送, phone: {}, signName: {}, templateCode: {}, templateParam: {}", mobile, signName, templateCode, templateParam);

            // 发送短信
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCodeWithOptions(request, new RuntimeOptions());

            log.info("==> 短信发送成功, response: {}", JsonUtils.toJsonString(response));
            return true;
        } catch (Exception e) {
            log.error("==> 短信发送失败：", e);
            return false;
        }
    }
}