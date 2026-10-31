package com.deepsea.lumenote.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.deepsea.lumenote.user.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.deepsea.lumenote")
public class LumenoteUserBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumenoteUserBizApplication.class, args);
    }

}
