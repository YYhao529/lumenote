package com.deepsea.framework.common;

import com.deepsea.framework.common.response.PageResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageResponseTest {

    @Test
    void shouldCalculateTotalPageWithGivenPageSize() {
        PageResponse<String> response = PageResponse.success(List.of("a", "b"), 2, 21, 10);

        assertTrue(response.isSuccess());
        assertEquals(2, response.getPageNo());
        assertEquals(10, response.getPageSize());
        assertEquals(21, response.getTotalCount());
        assertEquals(3, response.getTotalPage());
        assertEquals(2, response.getData().size());
    }

    @Test
    void shouldReturnZeroTotalPageWhenPageSizeIsZero() {
        PageResponse<String> response = PageResponse.success(List.of(), 1, 100, 0);

        assertEquals(0, response.getTotalPage());
    }

    @Test
    void shouldCalculateOffsetAndTotalPage() {
        assertEquals(0, PageResponse.getOffset(0, 10));
        assertEquals(20, PageResponse.getOffset(3, 10));
        assertEquals(3, PageResponse.getTotalPage(21, 10));
        assertEquals(0, PageResponse.getTotalPage(0, 10));
    }
}
