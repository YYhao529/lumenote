package com.deepsea.lumenote.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.deepsea.lumenote.search.domain.mapper")
public class LumenoteSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumenoteSearchApplication.class, args);
    }
}
