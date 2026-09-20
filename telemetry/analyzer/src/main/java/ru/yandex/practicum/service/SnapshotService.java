package ru.yandex.practicum.service;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.grpc.HubRouterClient;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.model.Scenario;
import ru.yandex.practicum.repository.ScenarioRepository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioEvaluator scenarioEvaluator;
    private final HubRouterClient hubRouterClient;

    @Transactional(readOnly = true)
    public void processSnapshot(SensorsSnapshotAvro snapshot) {
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