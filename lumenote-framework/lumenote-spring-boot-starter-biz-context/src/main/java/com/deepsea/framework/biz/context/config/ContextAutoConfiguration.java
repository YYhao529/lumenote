package com.deepsea.framework.biz.context.config;

import com.deepsea.framework.biz.context.filter.HeaderUserId2ContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ContextAutoConfiguration {

    @Bean
    public HeaderUserId2ContextFilter headerUserId2ContextFilter(){
        return new HeaderUserId2ContextFilter();
    }
}
