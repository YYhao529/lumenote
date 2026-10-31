package com.deepsea.lumenote.user.biz.rpc;

import com.deepsea.framework.common.response.Response;
import com.deepsea.lumenote.oss.api.FileFeignApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储服务调用
 */
@Component
public class OssRpcService {

    @Autowired
    private FileFeignApi fileFeignApi;

    public String uploadFile(MultipartFile file) {
        // 调用对象存储服务上传文件
        Response<?> response = fileFeignApi.uploadFile(file);

        // 对返回结果进行判断
        if (!response.isSuccess()){
            return null;
        }

        // 返回图片的访问链接
        return (String)response.getData();
    }
}
