package ru.yandex.practicum.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.config.AnalyzerKafkaConfig;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.service.HubEventService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {

    private final Consumer<String, HubEventAvro> hubEventConsumer;
    private final HubEventService hubEventService;
    private final AnalyzerKafkaConfig kafkaConfig;

    @Override
    public void run() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(hubEventConsumer::wakeup));
            hubEventConsumer.subscribe(List.of(kafkaConfig.getHubEvent().getTopic()));

            while (true) {
                ConsumerRecords<String, HubEventAvro> records =
                        hubEventConsumer.poll(kafkaConfig.getHubEvent().getPollTimeout());

                for (var record : records) {
                    try {
                        hubEventService.handle(record.value());
                    } catch (Exception e) {
                        log.error("Ошибка обработки hub-события: {}", record.value(), e);
                    }
                }

                if (!records.isEmpty()) {
                    hubEventConsumer.commitSync();
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий хабов", e);
        } finally {
            try {
                hubEventConsumer.commitSync();
            } finally {
                log.info("Закрываем консьюмер hub-событий");
                hubEventConsumer.close();
            }
        }
    }
}