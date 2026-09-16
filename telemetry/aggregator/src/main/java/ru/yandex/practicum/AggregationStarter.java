package ru.yandex.practicum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
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

    private final Consumer<Void, SensorEventAvro> consumer;
    private final Producer<String, SpecificRecordBase> producer;

    private static final String SNAPSHOTS_TOPIC = "telemetry.snapshots.v1";
    private static final Map<String, SensorsSnapshotAvro> snapshots = new HashMap<>();

    public void start() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));
            consumer.subscribe(List.of("telemetry.sensors.v1"));

            while (true) {
                ConsumerRecords<Void, SensorEventAvro> records = consumer.poll(Duration.ofMillis(1000));
                for (var record : records) {
                    Optional<SensorsSnapshotAvro> snapshot = updateState(record.value());
                    snapshot.ifPresent(s -> {
                        ProducerRecord<String, SpecificRecordBase> producerRecord =
                                new ProducerRecord<>(SNAPSHOTS_TOPIC, s.getHubId(), s);
                        producer.send(producerRecord);
                        log.info("Отправлен снапшот: {}", s);
                    });
                }
                consumer.commitAsync();
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий", e);
        } finally {
            try {
                consumer.commitSync();
            } finally {
                consumer.close();
                producer.close();
            }
        }
    }

    private Optional<SensorsSnapshotAvro> updateState(SensorEventAvro event) {
        // 1. Получаем или создаём снапшот для хаба
        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(
                event.getHubId(),
                hubId -> SensorsSnapshotAvro.newBuilder()
                        .setHubId(hubId)
                        .setTimestamp(event.getTimestamp())
                        .setSensorsState(new HashMap<>())
                        .build()
        );

        // 2. Получаем старое состояние датчика
        SensorStateAvro oldState = snapshot.getSensorsState().get(event.getId());

        // 3. Если старое состояние есть — проверяем timestamp
        if (oldState != null) {
            // Если старое состояние новее или равно — игнорируем
            if (oldState.getTimestamp() >= event.getTimestamp()) {
                return Optional.empty();
            }
            // Если данные не изменились — игнорируем
            if (oldState.getData().equals(event.getPayload())) {
                return Optional.empty();
            }
        }

        // 4. Создаём новое состояние
        SensorStateAvro newState = SensorStateAvro.newBuilder()
                .setTimestamp(event.getTimestamp())
                .setData(event.getPayload())
                .build();

        // 5. Обновляем снапшот
        snapshot.getSensorsState().put(event.getId(), newState);
        snapshot.setTimestamp(event.getTimestamp());

        // 6. Возвращаем обновлённый снапшот
        return Optional.of(snapshot);
    }
}