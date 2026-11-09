package com.deepsea.lumenote.oss.biz.enums;

import com.deepsea.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {
    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("OSS-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("OSS-10001", "参数错误"),

    // ----------- 业务异常状态码 -----------
    FILE_EMPTY("OSS-20000", "文件为空"),
    FILE_TYPE_NOT_ALLOWED("OSS-20001", "不支持的文件类型，仅允许上传图片（jpg/jpeg/png/gif/webp/bmp）或视频（mp4/mov/webm）"),
    FILE_TOO_LARGE("OSS-20002", "文件太大，单个文件不能超过 200MB"),
    ;

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}
