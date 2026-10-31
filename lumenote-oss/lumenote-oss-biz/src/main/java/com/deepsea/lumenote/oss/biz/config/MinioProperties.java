package com.deepsea.lumenote.oss.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Minio 配置项
 */
@ConfigurationProperties(prefix = "storage.minio")
@Component
@Data
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
}
