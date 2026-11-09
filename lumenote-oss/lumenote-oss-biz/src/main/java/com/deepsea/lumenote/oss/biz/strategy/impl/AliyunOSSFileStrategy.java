package com.deepsea.lumenote.oss.biz.strategy.impl;

import com.aliyun.oss.OSS;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.lumenote.oss.biz.config.AliyunOSSProperties;
import com.deepsea.lumenote.oss.biz.enums.ResponseCodeEnum;
import com.deepsea.lumenote.oss.biz.strategy.FileStrategy;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传策略
 */
@Slf4j
public class AliyunOSSFileStrategy implements FileStrategy {

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;

    @Resource
    private OSS aliyunOSSClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("## 上传文件至阿里云 OSS ...");

        // 判断文件是否为空
        if (file == null || file.getSize() == 0) {
            log.error("==> 上传文件异常：文件大小为空 ...");
            throw new BizException(ResponseCodeEnum.FILE_EMPTY);
        }

        // 文件的原始名称
        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.isNotBlank(originalFileName)) {
            throw new BizException(ResponseCodeEnum.FILE_EMPTY);
        }

        // 生成存储对象的名称（将 UUID 字符串中的 - 替换成空字符串）
        String key = UUID.randomUUID().toString().replace("-", "");
        // 获取文件的后缀，如 .jpg；无后缀时也能正常上传
        String suffix = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            suffix = originalFileName.substring(dotIndex);
        }

        // 拼接上文件后缀，即为要存储的文件名
        String objectName = String.format("%s%s", key, suffix);

        log.info("==> 开始上传文件至阿里云 OSS, ObjectName: {}", objectName);

        // 上传文件至阿里云 OSS
        aliyunOSSClient.putObject(bucketName,objectName,new ByteArrayInputStream(file.getBytes()));

        // 返回文件的访问链接
        String url = String.format("https://%s.%s/%s", bucketName, aliyunOSSProperties.getEndpoint(), objectName);
        log.info("==> 上传文件至阿里云 OSS 成功，访问路径: {}", url);
        return url;
    }
}
