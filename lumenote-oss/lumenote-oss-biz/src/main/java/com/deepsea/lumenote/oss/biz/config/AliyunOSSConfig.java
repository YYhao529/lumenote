package com.deepsea.lumenote.oss.biz.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS Client 配置
 */
@Configuration
public class AliyunOSSConfig {

    @Autowired
    private AliyunOSSProperties aliyunOSSProperties;

    /**
     * 构建阿里云 OSS Client
     */
    @Bean
    public OSS aliyunOSSClient() {
        // 从配置文件中获取 AccessKey 和 SecretKey
        DefaultCredentialProvider defaultCredentialProvider = CredentialsProviderFactory.newDefaultCredentialProvider(
                aliyunOSSProperties.getAccessKey(), aliyunOSSProperties.getSecretKey());

        // 创建 OSSClient 实例
        return OSSClientBuilder.create()
                .endpoint(aliyunOSSProperties.getEndpoint())
                .credentialsProvider(defaultCredentialProvider)
                .build();
    }
}
