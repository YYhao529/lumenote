package com.deepsea.lumenote.auth.sms;

import com.aliyun.credentials.models.Config;
import com.aliyun.dypnsapi20170525.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短信发送客户端
 */
@Slf4j
@Configuration
public class AliyunSmsClientConfig {

    @Value("${aliyun.sms.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.sms.endpoint}")
    private String endpoint;

    @Bean
    public Client smsClient() {
        try {
            // 1. 构建阿里云凭据配置，使用 AccessKey 方式进行身份认证
            Config credentialConfig = new Config()
                    .setType("access_key")
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);

            // 2. 创建凭据客户端，用于管理身份认证信息
            com.aliyun.credentials.Client credentialClient = new com.aliyun.credentials.Client(credentialConfig);

            // 3. 构建 OpenAPI 配置，绑定凭据客户端和服务接入点（Endpoint）
            com.aliyun.teaopenapi.models.Config openApiConfig = new com.aliyun.teaopenapi.models.Config()
                    .setCredential(credentialClient)
                    .setEndpoint(endpoint);

            // 4. 创建并返回号码认证服务（Dypnsapi）客户端
            return new Client(openApiConfig);
        } catch (Exception e) {
            log.error("初始化阿里云短信发送客户端错误: ", e);
            return null;
        }
    }
}