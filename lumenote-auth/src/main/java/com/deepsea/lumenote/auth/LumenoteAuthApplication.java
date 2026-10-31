package com.deepsea.lumenote.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.deepsea.lumenote")
public class LumenoteAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumenoteAuthApplication.class, args);
    }

}
