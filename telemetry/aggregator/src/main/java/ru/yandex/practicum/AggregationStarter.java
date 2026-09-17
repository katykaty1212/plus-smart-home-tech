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
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {

    private final Consumer<String, SensorEventAvro> consumer;
    private final Producer<String, SpecificRecordBase> producer;

    @Value("${kafka.topics.sensors}")
    private String sensorsTopic;

    @Value("${kafka.topics.snapshots}")
    private String snapshotsTopic;

    private final Map<String, SensorsSnapshotAvro> snapshots = new HashMap<>();

    public void start() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));
            consumer.subscribe(List.of(sensorsTopic));

            while (true) {
                ConsumerRecords<String, SensorEventAvro> records = consumer.poll(Duration.ofMillis(100));
                for (var record : records) {
                    Optional<SensorsSnapshotAvro> snapshot = updateState(record.value());
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
                consumer.commitSync();
            } finally {
                log.info("Закрываем консьюмер");
                consumer.close();
                log.info("Закрываем продюсер");
                producer.flush();
                producer.close();
            }
        }
    }

    private Optional<SensorsSnapshotAvro> updateState(SensorEventAvro event) {
        SensorsSnapshotAvro oldSnapshot = snapshots.get(event.getHubId());

        Map<String, SensorStateAvro> newSensorsState = oldSnapshot != null
                ? new HashMap<>(oldSnapshot.getSensorsState())
                : new HashMap<>();

        SensorStateAvro oldState = newSensorsState.get(event.getId());

        if (oldState != null) {
            boolean olderEvent = oldState.getTimestamp().toEpochMilli()
                    > event.getTimestamp().toEpochMilli();
            boolean sameData = oldState.getData().toString()
                    .equals(event.getPayload().toString());

            if (olderEvent || sameData) {
                return Optional.empty();
            }
        }

        SensorStateAvro newState = SensorStateAvro.newBuilder()
                .setTimestamp(event.getTimestamp())
                .setData(event.getPayload())
                .build();

        newSensorsState.put(event.getId(), newState);

        SensorsSnapshotAvro newSnapshot = SensorsSnapshotAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp())
                .setSensorsState(newSensorsState)
                .build();

        snapshots.put(event.getHubId(), newSnapshot);
        return Optional.of(newSnapshot);
    }
}