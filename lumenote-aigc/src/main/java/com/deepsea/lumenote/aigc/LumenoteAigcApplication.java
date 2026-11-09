package com.deepsea.lumenote.aigc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.deepsea.lumenote.aigc.domain.mapper")
@EnableFeignClients(basePackages = "com.deepsea.lumenote")
public class LumenoteAigcApplication {
    public static void main(String[] args) {
        SpringApplication.run(LumenoteAigcApplication.class, args);
    }
}
