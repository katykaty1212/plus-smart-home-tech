package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class SnapshotService {

    private final Map<String, SensorsSnapshotAvro> snapshots = new HashMap<>();

    public Optional<SensorsSnapshotAvro> updateState(SensorEventAvro event) {
        SensorsSnapshotAvro oldSnapshot = snapshots.get(event.getHubId());

        Map<String, SensorStateAvro> newSensorsState = oldSnapshot != null
                ? new HashMap<>(oldSnapshot.getSensorsState())
                : new HashMap<>();

        SensorStateAvro oldState = newSensorsState.get(event.getId());

        if (oldState != null) {
            boolean olderEvent = oldState.getTimestamp().toEpochMilli()
                    > event.getTimestamp().toEpochMilli();
            boolean sameData = isSameData(oldState.getData(), event.getPayload());

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

    private boolean isSameData(Object data1, Object data2) {
        if (data1 == null || data2 == null) {
            return data1 == data2;
        }
        return java.util.Arrays.equals(serialize(data1), serialize(data2));
    }

    private byte[] serialize(Object record) {
        try (var out = new java.io.ByteArrayOutputStream()) {
            var encoder = org.apache.avro.io.EncoderFactory.get()
                    .binaryEncoder(out, null);
            var specific = (org.apache.avro.specific.SpecificRecordBase) record;
            var writer = new org.apache.avro.specific.SpecificDatumWriter<>(
                    specific.getSchema());
            writer.write(specific, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации Avro-записи", e);
        }
    }
}