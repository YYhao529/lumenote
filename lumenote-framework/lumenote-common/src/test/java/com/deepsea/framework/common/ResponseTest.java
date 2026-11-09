package com.deepsea.framework.common;

import com.deepsea.framework.common.exception.BaseExceptionInterface;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.framework.common.response.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseTest {

    @Test
    void shouldBuildSuccessResponse() {
        Response<String> response = Response.success("data");

        assertTrue(response.isSuccess());
        assertEquals("data", response.getData());
        assertNull(response.getErrorCode());
    }

    @Test
    void shouldBuildFailResponseWithCode() {
        Response<String> response = Response.fail("USER-20007", "该用户不存在");

        assertFalse(response.isSuccess());
        assertEquals("USER-20007", response.getErrorCode());
        assertEquals("该用户不存在", response.getMessage());
    }

    @Test
    void shouldBuildFailResponseFromBizException() {
        BaseExceptionInterface error = new BaseExceptionInterface() {
            @Override
            public String getErrorCode() {
                return "AUTH-20004";
            }

            @Override
            public String getErrorMessage() {
                return "手机号或密码错误";
            }
        };

        Response<String> response = Response.fail(new BizException(error));

        assertFalse(response.isSuccess());
        assertEquals("AUTH-20004", response.getErrorCode());
        assertEquals("手机号或密码错误", response.getMessage());
    }
}
