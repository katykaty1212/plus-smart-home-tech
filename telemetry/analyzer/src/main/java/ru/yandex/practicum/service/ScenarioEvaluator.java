package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.model.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioEvaluator {

    public List<MatchedAction> evaluate(SensorsSnapshotAvro snapshot, List<Scenario> scenarios) {
        List<MatchedAction> result = new ArrayList<>();

        for (Scenario scenario : scenarios) {
            if (matches(scenario, snapshot)) {
                for (ScenarioAction sa : scenario.getActions()) {
                    result.add(new MatchedAction(scenario, sa.getSensor(), sa.getAction()));
                }
            }
        }
        return result;
    }

    private boolean matches(Scenario scenario, SensorsSnapshotAvro snapshot) {
        for (ScenarioCondition sc : scenario.getConditions()) {
            if (!conditionMatches(sc, snapshot)) {
                return false;
            }
        }
        return true;
    }

    private boolean conditionMatches(ScenarioCondition sc, SensorsSnapshotAvro snapshot) {
        String sensorId = sc.getSensor().getId();
        SensorStateAvro state = snapshot.getSensorsState().get(sensorId);
        if (state == null) {
            return false;
        }

        Integer actual = extractSensorValue(state.getData(), sc.getCondition().getType());
        if (actual == null) {
            return false;
        }

        Integer expected = sc.getCondition().getValue();
        if (expected == null) {
            return false;
        }

        return switch (sc.getCondition().getOperation()) {
            case EQUALS -> actual.equals(expected);
            case GREATER_THAN -> actual > expected;
            case LOWER_THAN -> actual < expected;
        };
    }

    private Integer extractSensorValue(Object data, ConditionType type) {
        return switch (type) {
            case MOTION -> data instanceof MotionSensorAvro m ? (m.getMotion() ? 1 : 0) : null;
            case LUMINOSITY -> data instanceof LightSensorAvro l ? l.getLuminosity() : null;
            case SWITCH -> data instanceof SwitchSensorAvro s ? (s.getState() ? 1 : 0) : null;
            case TEMPERATURE -> data instanceof TemperatureSensorAvro t ? t.getTemperatureC() : null;
            case CO2LEVEL -> data instanceof ClimateSensorAvro c ? c.getCo2Level() : null;
            case HUMIDITY -> data instanceof ClimateSensorAvro c ? c.getHumidity() : null;
        };
    }

    public record MatchedAction(Scenario scenario, Sensor sensor, Action action) {}
}