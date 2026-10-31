package com.deepsea.lumenote.distributed.id.generator.biz.controller;

import com.deepsea.lumenote.distributed.id.generator.biz.core.common.Result;
import com.deepsea.lumenote.distributed.id.generator.biz.core.common.Status;
import com.deepsea.lumenote.distributed.id.generator.biz.exception.LeafServerException;
import com.deepsea.lumenote.distributed.id.generator.biz.exception.NoKeyException;
import com.deepsea.lumenote.distributed.id.generator.biz.service.SegmentService;
import com.deepsea.lumenote.distributed.id.generator.biz.service.SnowflakeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/id")
@Slf4j
public class LeafController {

    @Autowired
    private SegmentService segmentService;
    @Autowired
    private SnowflakeService snowflakeService;

    @GetMapping(value = "/segment/get/{key}")
    public String getSegmentId(@PathVariable("key") String key) {
        return get(key, segmentService.getId(key));
    }

    @GetMapping(value = "/snowflake/get/{key}")
    public String getSnowflakeId(@PathVariable("key") String key) {
        return get(key, snowflakeService.getId(key));
    }

    private String get(@PathVariable("key") String key, Result id) {
        Result result;
        if (key == null || key.isEmpty()) {
            throw new NoKeyException();
        }
        result = id;
        if (result.getStatus().equals(Status.EXCEPTION)) {
            throw new LeafServerException(result.toString());
        }
        return String.valueOf(result.getId());
    }
}
