package com.deepsea.lumenote.user.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.deepsea.lumenote.user.relation.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.deepsea.lumenote")
public class LumenoteUserRelationBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumenoteUserRelationBizApplication.class, args);
    }
}
