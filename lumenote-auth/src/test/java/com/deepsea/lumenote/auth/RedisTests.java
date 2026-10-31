package com.deepsea.lumenote.auth;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
@Slf4j
public class RedisTests {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * set key value
     */
    @Test
    void testSetKeyValue() {
        redisTemplate.opsForValue().set("name", "深海");
    }

    /**
     * 判断某个 key 是否存在
     */
    @Test
    void testHasKey(){
        log.info("key 是否存在:{}", redisTemplate.hasKey("name"));
    }

    /**
     * 获取某个 key 的 value
     */
    @Test
    void testGetValue(){
        String name = (String)redisTemplate.opsForValue().get("name");
        log.info("value:{}",name);
    }

    /**
     * 删除某个 key
     */
    @Test
    void testDelete(){
        redisTemplate.delete("name");
    }
}
