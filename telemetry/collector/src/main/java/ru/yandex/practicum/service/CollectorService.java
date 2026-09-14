package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.model.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

    private final KafkaProducerService producerService;

    @Value("${kafka.topics.sensors}")
    private String sensorsTopic;

    @Value("${kafka.topics.hubs}")
    private String hubsTopic;

    public void collectSensorEvent(SensorEvent event) {
        SensorEventAvro avro = toSensorAvro(event);
        producerService.send(sensorsTopic, event.getHubId(),
                event.getTimestamp().toEpochMilli(), avro);
    }

    public void collectHubEvent(HubEvent event) {
        HubEventAvro avro = toHubAvro(event);
        producerService.send(hubsTopic, event.getHubId(),
                event.getTimestamp().toEpochMilli(), avro);
    }

    private SensorEventAvro toSensorAvro(SensorEvent event) {
        Object payload = switch (event.getType()) {
            case LIGHT_SENSOR_EVENT -> {
                LightSensorEvent e = (LightSensorEvent) event;
                yield LightSensorAvro.newBuilder()
                        .setLinkQuality(e.getLinkQuality())
                        .setLuminosity(e.getLuminosity())
                        .build();
            }
            case SWITCH_SENSOR_EVENT -> {
                SwitchSensorEvent e = (SwitchSensorEvent) event;
                yield SwitchSensorAvro.newBuilder()
                        .setState(e.isState())
                        .build();
            }
            case CLIMATE_SENSOR_EVENT -> {
                ClimateSensorEvent e = (ClimateSensorEvent) event;
                yield ClimateSensorAvro.newBuilder()
                        .setTemperatureC(e.getTemperatureC())
                        .setHumidity(e.getHumidity())
                        .setCo2Level(e.getCo2Level())
                        .build();
            }
            case MOTION_SENSOR_EVENT -> {
                MotionSensorEvent e = (MotionSensorEvent) event;
                yield MotionSensorAvro.newBuilder()
                        .setLinkQuality(e.getLinkQuality())
                        .setMotion(e.isMotion())
                        .setVoltage(e.getVoltage())
                        .build();
            }
            case TEMPERATURE_SENSOR_EVENT -> {
                TemperatureSensorEvent e = (TemperatureSensorEvent) event;
                yield TemperatureSensorAvro.newBuilder()
                        .setId(e.getId())
                        .setHubId(e.getHubId())
                        .setTimestamp(e.getTimestamp().toEpochMilli())
                        .setTemperatureC(e.getTemperatureC())
                        .setTemperatureF(e.getTemperatureF())
                        .build();
            }
        };

        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp().toEpochMilli())
                .setPayload(payload)
                .build();
    }

    private HubEventAvro toHubAvro(HubEvent event) {
        Object payload = switch (event.getType()) {
            case DEVICE_ADDED -> {
                DeviceAddedEvent e = (DeviceAddedEvent) event;
                yield DeviceAddedEventAvro.newBuilder()
                        .setId(e.getId())
                        .setType(DeviceTypeAvro.valueOf(e.getDeviceType().name()))
                        .build();
            }
            case DEVICE_REMOVED -> {
                DeviceRemovedEvent e = (DeviceRemovedEvent) event;
                yield DeviceRemovedEventAvro.newBuilder()
                        .setId(e.getId())
                        .build();
            }
            case SCENARIO_ADDED -> {
                ScenarioAddedEvent e = (ScenarioAddedEvent) event;
                yield ScenarioAddedEventAvro.newBuilder()
                        .setName(e.getName())
                        .setConditions(e.getConditions().stream()
                                .map(c -> ScenarioConditionAvro.newBuilder()
                                        .setSensorId(c.getSensorId())
                                        .setType(ConditionTypeAvro.valueOf(c.getType().name()))
                                        .setOperation(ConditionOperationAvro.valueOf(c.getOperation().name()))
                                        .setValue(c.getValue())
                                        .build())
                                .toList())
                        .setActions(e.getActions().stream()
                                .map(a -> DeviceActionAvro.newBuilder()
                                        .setSensorId(a.getSensorId())
                                        .setType(ActionTypeAvro.valueOf(a.getType().name()))
                                        .setValue(a.getValue())
                                        .build())
                                .toList())
                        .build();
            }
            case SCENARIO_REMOVED -> {
                ScenarioRemovedEvent e = (ScenarioRemovedEvent) event;
                yield ScenarioRemovedEventAvro.newBuilder()
                        .setName(e.getName())
                        .build();
            }
        };

        return HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp().toEpochMilli())
                .setPayload(payload)
                .build();
    }
}