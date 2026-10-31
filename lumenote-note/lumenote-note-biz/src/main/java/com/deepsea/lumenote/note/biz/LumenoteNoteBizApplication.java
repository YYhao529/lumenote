package com.deepsea.lumenote.note.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.deepsea.lumenote.note.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.deepsea.lumenote")
public class LumenoteNoteBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumenoteNoteBizApplication.class, args);
    }
}
