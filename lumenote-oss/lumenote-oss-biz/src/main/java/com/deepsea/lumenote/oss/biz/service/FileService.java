package com.deepsea.lumenote.oss.biz.service;

import com.deepsea.framework.common.response.Response;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * 上传文件
     */
    Response<?> uploadFile(MultipartFile file);
}
