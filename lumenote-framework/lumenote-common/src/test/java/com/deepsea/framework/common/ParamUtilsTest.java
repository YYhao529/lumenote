package com.deepsea.framework.common;

import com.deepsea.framework.common.util.ParamUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamUtilsTest {

    @Test
    void shouldValidateNickname() {
        assertTrue(ParamUtils.checkNickname("拾光笔记"));
        assertTrue(ParamUtils.checkNickname("ab"));
        assertFalse(ParamUtils.checkNickname("a"));
        assertFalse(ParamUtils.checkNickname("bad!name"));
        assertFalse(ParamUtils.checkNickname("a".repeat(25)));
        assertFalse(ParamUtils.checkNickname(null));
    }

    @Test
    void shouldValidateLumenoteId() {
        assertTrue(ParamUtils.checkLumenoteId("abc_123"));
        assertTrue(ParamUtils.checkLumenoteId("ABC123"));
        assertFalse(ParamUtils.checkLumenoteId("abc"));
        assertFalse(ParamUtils.checkLumenoteId("abc-123"));
        assertFalse(ParamUtils.checkLumenoteId("abc 123"));
        assertFalse(ParamUtils.checkLumenoteId(null));
    }

    @Test
    void shouldValidateLength() {
        assertTrue(ParamUtils.checkLength("abc", 3));
        assertFalse(ParamUtils.checkLength("abcd", 3));
        assertFalse(ParamUtils.checkLength("", 3));
        assertFalse(ParamUtils.checkLength(null, 3));
    }
}
