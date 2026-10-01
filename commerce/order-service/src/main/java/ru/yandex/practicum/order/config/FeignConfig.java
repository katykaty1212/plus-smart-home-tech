package ru.yandex.practicum.order.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Value("${app.source-service}")
    private String sourceService;

    @Bean
    public RequestInterceptor sourceServiceHeaderInterceptor() {
        return template -> template.header("X-Source-Service", sourceService);
    }
}