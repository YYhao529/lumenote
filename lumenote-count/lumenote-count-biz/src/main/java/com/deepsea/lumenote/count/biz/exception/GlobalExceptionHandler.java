package com.deepsea.lumenote.count.biz.exception;

import com.deepsea.framework.common.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;

/**
 * 全局异常处理
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获参数校验异常
     */
    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseBody
    public Response<Object> handleMethodArgumentNotValidException(HttpServletRequest request, MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();

        StringBuilder sb = new StringBuilder();
        Optional.of(bindingResult.getFieldErrors()).ifPresent(errors ->
                errors.forEach(error -> sb.append(error.getField())
                        .append(" ")
                        .append(error.getDefaultMessage())
                        .append("; ")));

        log.warn("{} request fail, errorMessage: {}", request.getRequestURI(), sb);
        return Response.fail("PARAM_NOT_VALID", sb.toString());
    }

    /**
     * 其他类型异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Response<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.warn("{} request error, ", request.getRequestURI(), e);
        return Response.fail("SYSTEM_ERROR", "系统异常，请稍后重试");
    }
}
