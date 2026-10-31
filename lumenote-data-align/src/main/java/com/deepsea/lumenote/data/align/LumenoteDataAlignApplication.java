package com.deepsea.lumenote.data.align;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.deepsea.lumenote.data.align.domain.mapper")
public class LumenoteDataAlignApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumenoteDataAlignApplication.class, args);
    }
}
