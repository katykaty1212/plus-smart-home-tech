package ru.yandex.practicum.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.service.HubEventService;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {

    private final Consumer<String, HubEventAvro> hubEventConsumer;
    private final HubEventService hubEventService;

    @Value("${kafka.topics.hubs}")
    private String hubsTopic;

    @Override
    public void run() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(hubEventConsumer::wakeup));
            hubEventConsumer.subscribe(List.of(hubsTopic));

            while (true) {
                ConsumerRecords<String, HubEventAvro> records =
                        hubEventConsumer.poll(Duration.ofMillis(1000));

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
            // shutdown
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