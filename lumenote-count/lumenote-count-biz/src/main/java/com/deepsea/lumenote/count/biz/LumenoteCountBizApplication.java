package com.deepsea.lumenote.count.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.deepsea.lumenote.count.biz.domain.mapper")
public class LumenoteCountBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumenoteCountBizApplication.class, args);
    }
}
