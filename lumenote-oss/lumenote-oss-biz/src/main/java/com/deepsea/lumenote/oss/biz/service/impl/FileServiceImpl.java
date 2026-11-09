package com.deepsea.lumenote.oss.biz.service.impl;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.oss.biz.service.FileService;
import com.deepsea.lumenote.oss.biz.strategy.FileStrategy;
import com.deepsea.lumenote.oss.biz.util.FileTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Autowired
    private FileStrategy fileStrategy;

    private static final String BUCKET_NAME = "deepsea-lumenote";

    @Override
    public Response<?> uploadFile(MultipartFile file) {
        // 安全校验：非空、大小、扩展名白名单、Content-Type、文件头魔数
        FileTypeValidator.validate(file);

        String url = fileStrategy.uploadFile(file, BUCKET_NAME);

        return Response.success(url);
    }
}
