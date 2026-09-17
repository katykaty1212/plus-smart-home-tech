package ru.yandex.practicum.grpc;

import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;

@Slf4j
@Component
public class HubRouterClient {

    private final HubRouterControllerGrpc.HubRouterControllerBlockingStub stub;

    public HubRouterClient(
            @GrpcClient("hub-router")
            HubRouterControllerGrpc.HubRouterControllerBlockingStub stub) {
        this.stub = stub;
    }

    public void sendAction(String hubId,
                           String scenarioName,
                           DeviceActionProto action,
                           Timestamp timestamp) {
        DeviceActionRequest request = DeviceActionRequest.newBuilder()
                .setHubId(hubId)
                .setScenarioName(scenarioName)
                .setAction(action)
                .setTimestamp(timestamp)
                .build();

        try {
            stub.handleDeviceAction(request);
            log.info("Отправлено действие {} для хаба {} (сценарий {})",
                    action, hubId, scenarioName);
        } catch (Exception e) {
            log.error("Ошибка отправки действия для хаба {}", hubId, e);
        }
    }
}