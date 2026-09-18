package ru.yandex.practicum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Properties;

@Getter
@Setter
@ConfigurationProperties("analyzer.kafka")
public class AnalyzerKafkaConfig {

    private String bootstrapServers;
    private Consumer hubEvent;
    private Consumer snapshot;

    @Getter
    @Setter
    public static class Consumer {
        private String topic;
        private Duration pollTimeout;
        private Properties properties;
    }
}