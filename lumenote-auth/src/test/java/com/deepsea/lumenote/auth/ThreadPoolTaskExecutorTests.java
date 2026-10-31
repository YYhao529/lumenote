package com.deepsea.lumenote.auth;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootTest
@Slf4j
public class ThreadPoolTaskExecutorTests {

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 测试线程池
     */
    @Test
    void testSubmit(){
        Runnable task=()->{
            log.info("枪若白狼，一收一放");
        };
        threadPoolTaskExecutor.submit(task);
    }

}
