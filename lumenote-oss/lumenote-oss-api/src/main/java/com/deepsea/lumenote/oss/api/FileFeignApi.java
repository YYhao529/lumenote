package com.deepsea.lumenote.oss.api;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.oss.config.FeignFormConfig;
import com.deepsea.lumenote.oss.constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = ApiConstants.SERVICE_NAME,configuration = FeignFormConfig.class)
public interface FileFeignApi {

    String PREFIX = "/file";

    /**
     * 文件上传
     */
    @PostMapping(value = PREFIX + "/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file);

}
