package ru.yandex.practicum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.service.SnapshotService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {

    private final Consumer<String, SensorEventAvro> consumer;
    private final Producer<String, SpecificRecordBase> producer;
    private final SnapshotService snapshotService;

    @Value("${kafka.topics.sensors}")
    private String sensorsTopic;

    @Value("${kafka.topics.snapshots}")
    private String snapshotsTopic;

    public void start() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));
            consumer.subscribe(List.of(sensorsTopic));

            while (true) {
                ConsumerRecords<String, SensorEventAvro> records =
                        consumer.poll(Duration.ofMillis(100));

                for (var record : records) {
                    Optional<SensorsSnapshotAvro> snapshot =
                            snapshotService.updateState(record.value());

                    snapshot.ifPresent(s -> {
                        ProducerRecord<String, SpecificRecordBase> producerRecord =
                                new ProducerRecord<>(snapshotsTopic, s.getHubId(), s);

                        producer.send(producerRecord, (metadata, exception) -> {
                            if (exception != null) {
                                log.error("Ошибка отправки снапшота в топик {}", snapshotsTopic, exception);
                            } else {
                                log.info("Отправлен снапшот: {}", s);
                            }
                        });
                    });
                }

                consumer.commitAsync((offsets, exception) -> {
                    if (exception != null) {
                        log.error("Ошибка коммита оффсетов: {}", offsets, exception);
                    }
                });
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий", e);
        } finally {
            try {
                log.info("Сбрасываем продюсер");
                producer.flush();
                log.info("Фиксируем смещения");
                consumer.commitSync();
            } finally {
                log.info("Закрываем консьюмер");
                consumer.close();
                log.info("Закрываем продюсер");
                producer.close();
            }
        }
    }
}