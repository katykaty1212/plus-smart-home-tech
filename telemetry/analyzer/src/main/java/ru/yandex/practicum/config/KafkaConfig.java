package ru.yandex.practicum.config;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaConsumer<String, HubEventAvro> hubEventConsumer(AnalyzerKafkaConfig config) {
        return new KafkaConsumer<>(config.getHubEvent().getProperties());
    }

    @Bean
    public KafkaConsumer<String, SensorsSnapshotAvro> snapshotConsumer(AnalyzerKafkaConfig config) {
        return new KafkaConsumer<>(config.getSnapshot().getProperties());
    }
}