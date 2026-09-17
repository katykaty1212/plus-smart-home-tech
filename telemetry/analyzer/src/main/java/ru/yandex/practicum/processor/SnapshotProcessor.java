package ru.yandex.practicum.processor;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.grpc.HubRouterClient;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.model.Scenario;
import ru.yandex.practicum.repository.ScenarioRepository;
import ru.yandex.practicum.service.ScenarioEvaluator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotProcessor {

    private final Consumer<String, SensorsSnapshotAvro> snapshotConsumer;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioEvaluator scenarioEvaluator;
    private final HubRouterClient hubRouterClient;

    @Value("${kafka.topics.snapshots}")
    private String snapshotsTopic;

    public void start() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(snapshotConsumer::wakeup));
            snapshotConsumer.subscribe(List.of(snapshotsTopic));

            while (true) {
                ConsumerRecords<String, SensorsSnapshotAvro> records =
                        snapshotConsumer.poll(Duration.ofMillis(1000));

                for (var record : records) {
                    try {
                        processSnapshot(record.value());
                    } catch (Exception e) {
                        log.error("Ошибка обработки снапшота: {}", record.value(), e);
                    }
                }

                if (!records.isEmpty()) {
                    snapshotConsumer.commitSync();
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время обработки снапшотов", e);
        } finally {
            try {
                snapshotConsumer.commitSync();
            } finally {
                log.info("Закрываем консьюмер снапшотов");
                snapshotConsumer.close();
            }
        }
    }

    private void processSnapshot(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId();
        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);
        if (scenarios.isEmpty()) {
            log.debug("Для хаба {} нет сценариев", hubId);
            return;
        }

        var matched = scenarioEvaluator.evaluate(snapshot, scenarios);
        if (matched.isEmpty()) {
            log.debug("Ни один сценарий хаба {} не сработал", hubId);
            return;
        }

        Instant eventTime = snapshot.getTimestamp();
        Timestamp grpcTimestamp = Timestamp.newBuilder()
                .setSeconds(eventTime.getEpochSecond())
                .setNanos(eventTime.getNano())
                .build();

        for (var m : matched) {
            DeviceActionProto.Builder actionBuilder = DeviceActionProto.newBuilder()
                    .setSensorId(m.sensor().getId())
                    .setType(ActionTypeProto.valueOf(m.action().getType().name()));

            if (m.action().getValue() != null) {
                actionBuilder.setValue(m.action().getValue());
            }

            hubRouterClient.sendAction(
                    hubId,
                    m.scenario().getName(),
                    actionBuilder.build(),
                    grpcTimestamp);
        }
    }
}