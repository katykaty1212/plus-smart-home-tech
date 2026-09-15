package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

    private final KafkaProducerService producerService;

    @Value("${kafka.topics.sensors}")
    private String sensorsTopic;

    @Value("${kafka.topics.hubs}")
    private String hubsTopic;

    public void collectSensorEvent(SensorEventProto event) {
        SensorEventAvro avro = toSensorAvro(event);
        producerService.send(sensorsTopic, event.getHubId(),
                event.getTimestamp().getSeconds() * 1000 + event.getTimestamp().getNanos() / 1_000_000, avro);
    }

    public void collectHubEvent(HubEventProto event) {
        HubEventAvro avro = toHubAvro(event);
        producerService.send(hubsTopic, event.getHubId(),
                event.getTimestamp().getSeconds() * 1000 + event.getTimestamp().getNanos() / 1_000_000, avro);
    }

    private SensorEventAvro toSensorAvro(SensorEventProto event) {
        Object payload = switch (event.getPayloadCase()) {
            case LIGHT_SENSOR -> {
                LightSensorProto p = event.getLightSensor();
                yield LightSensorAvro.newBuilder()
                        .setLinkQuality(p.getLinkQuality())
                        .setLuminosity(p.getLuminosity())
                        .build();
            }
            case SWITCH_SENSOR -> {
                SwitchSensorProto p = event.getSwitchSensor();
                yield SwitchSensorAvro.newBuilder()
                        .setState(p.getState())
                        .build();
            }
            case CLIMATE_SENSOR -> {
                ClimateSensorProto p = event.getClimateSensor();
                yield ClimateSensorAvro.newBuilder()
                        .setTemperatureC(p.getTemperatureC())
                        .setHumidity(p.getHumidity())
                        .setCo2Level(p.getCo2Level())
                        .build();
            }
            case MOTION_SENSOR -> {
                MotionSensorProto p = event.getMotionSensor();
                yield MotionSensorAvro.newBuilder()
                        .setLinkQuality(p.getLinkQuality())
                        .setMotion(p.getMotion())
                        .setVoltage(p.getVoltage())
                        .build();
            }
            case TEMPERATURE_SENSOR -> {
                TemperatureSensorProto p = event.getTemperatureSensor();
                yield TemperatureSensorAvro.newBuilder()
                        .setId(event.getId())
                        .setHubId(event.getHubId())
                        .setTimestamp(event.getTimestamp().getSeconds() * 1000 + event.getTimestamp().getNanos() / 1_000_000)
                        .setTemperatureC(p.getTemperatureC())
                        .setTemperatureF(p.getTemperatureF())
                        .build();
            }
            default -> throw new IllegalArgumentException("Неизвестный тип события: " + event.getPayloadCase());
        };

        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp().getSeconds() * 1000 + event.getTimestamp().getNanos() / 1_000_000)
                .setPayload(payload)
                .build();
    }

    private HubEventAvro toHubAvro(HubEventProto event) {
        Object payload = switch (event.getPayloadCase()) {
            case DEVICE_ADDED -> {
                DeviceAddedEventProto p = event.getDeviceAdded();
                yield DeviceAddedEventAvro.newBuilder()
                        .setId(p.getId())
                        .setType(DeviceTypeAvro.valueOf(p.getType().name()))
                        .build();
            }
            case DEVICE_REMOVED -> {
                DeviceRemovedEventProto p = event.getDeviceRemoved();
                yield DeviceRemovedEventAvro.newBuilder()
                        .setId(p.getId())
                        .build();
            }
            case SCENARIO_ADDED -> {
                ScenarioAddedEventProto p = event.getScenarioAdded();
                yield ScenarioAddedEventAvro.newBuilder()
                        .setName(p.getName())
                        .setConditions(p.getConditionList().stream()
                                .map(c -> ScenarioConditionAvro.newBuilder()
                                        .setSensorId(c.getSensorId())
                                        .setType(ConditionTypeAvro.valueOf(c.getType().name()))
                                        .setOperation(ConditionOperationAvro.valueOf(c.getOperation().name()))
                                        .setValue(c.hasIntValue() ? c.getIntValue() : (c.getBoolValue() ? 1 : 0))
                                        .build())
                                .toList())
                        .setActions(p.getActionList().stream()
                                .map(a -> DeviceActionAvro.newBuilder()
                                        .setSensorId(a.getSensorId())
                                        .setType(ActionTypeAvro.valueOf(a.getType().name()))
                                        .setValue(a.getValue())
                                        .build())
                                .toList())
                        .build();
            }
            case SCENARIO_REMOVED -> {
                ScenarioRemovedEventProto p = event.getScenarioRemoved();
                yield ScenarioRemovedEventAvro.newBuilder()
                        .setName(p.getName())
                        .build();
            }
            default -> throw new IllegalArgumentException("Неизвестный тип события: " + event.getPayloadCase());
        };

        return HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp().getSeconds() * 1000 + event.getTimestamp().getNanos() / 1_000_000)
                .setPayload(payload)
                .build();
    }
}