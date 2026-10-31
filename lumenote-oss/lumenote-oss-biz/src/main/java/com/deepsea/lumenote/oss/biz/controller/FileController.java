package com.deepsea.lumenote.oss.biz.controller;

import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.oss.biz.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping(value = "/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> uploadFile(@RequestPart("file") MultipartFile file) {
        log.info("当前用户 ID: {}", LoginUserContextHolder.getUserId());
        return fileService.uploadFile(file);
    }
}
