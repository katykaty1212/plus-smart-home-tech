package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.model.*;
import ru.yandex.practicum.repository.ActionRepository;
import ru.yandex.practicum.repository.ConditionRepository;
import ru.yandex.practicum.repository.ScenarioRepository;
import ru.yandex.practicum.repository.SensorRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HubEventService {

    private final SensorRepository sensorRepository;
    private final ScenarioRepository scenarioRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

    @Transactional
    public void handle(HubEventAvro event) {
        Object payload = event.getPayload();
        if (payload instanceof DeviceAddedEventAvro e) {
            addDevice(event.getHubId(), e);
        } else if (payload instanceof DeviceRemovedEventAvro e) {
            removeDevice(event.getHubId(), e);
        } else if (payload instanceof ScenarioAddedEventAvro e) {
            addScenario(event.getHubId(), e);
        } else if (payload instanceof ScenarioRemovedEventAvro e) {
            removeScenario(event.getHubId(), e);
        } else {
            log.warn("Неизвестный тип HubEventAvro: {}", payload.getClass());
        }
    }

    private void addDevice(String hubId, DeviceAddedEventAvro event) {
        if (sensorRepository.findByIdAndHubId(event.getId(), hubId).isPresent()) {
            log.info("Датчик {} уже существует в хабе {}", event.getId(), hubId);
            return;
        }
        Sensor sensor = new Sensor();
        sensor.setId(event.getId());
        sensor.setHubId(hubId);
        sensorRepository.save(sensor);
        log.info("Добавлен датчик {} в хаб {}", event.getId(), hubId);
    }

    private void removeDevice(String hubId, DeviceRemovedEventAvro event) {
        sensorRepository.findByIdAndHubId(event.getId(), hubId)
                .ifPresent(sensorRepository::delete);
    }

    private void addScenario(String hubId, ScenarioAddedEventAvro event) {
        scenarioRepository.findByHubIdAndName(hubId, event.getName())
                .ifPresent(s -> {
                    scenarioRepository.delete(s);
                    scenarioRepository.flush();
                });

        Scenario scenario = new Scenario();
        scenario.setHubId(hubId);
        scenario.setName(event.getName());

        List<ScenarioCondition> scenarioConditions = new ArrayList<>();
        for (ScenarioConditionAvro cond : event.getConditions()) {
            Optional<Sensor> sensor = sensorRepository.findByIdAndHubId(cond.getSensorId(), hubId);
            if (sensor.isEmpty()) {
                log.warn("Датчик {} не найден в хабе {}, условие пропущено",
                        cond.getSensorId(), hubId);
                continue;
            }

            Condition condition = new Condition();
            condition.setType(ConditionType.valueOf(cond.getType().name()));
            condition.setOperation(ConditionOperation.valueOf(cond.getOperation().name()));
            condition.setValue(extractValue(cond.getValue()));
            conditionRepository.save(condition);

            ScenarioCondition sc = new ScenarioCondition();
            sc.setScenario(scenario);
            sc.setSensor(sensor.get());
            sc.setCondition(condition);
            scenarioConditions.add(sc);
        }

        List<ScenarioAction> scenarioActions = new ArrayList<>();
        for (DeviceActionAvro act : event.getActions()) {
            Optional<Sensor> sensor = sensorRepository.findByIdAndHubId(act.getSensorId(), hubId);
            if (sensor.isEmpty()) {
                log.warn("Датчик {} не найден в хабе {}, действие пропущено",
                        act.getSensorId(), hubId);
                continue;
            }

            Action action = new Action();
            action.setType(ActionType.valueOf(act.getType().name()));
            action.setValue(act.getValue());
            actionRepository.save(action);

            ScenarioAction sa = new ScenarioAction();
            sa.setScenario(scenario);
            sa.setSensor(sensor.get());
            sa.setAction(action);
            scenarioActions.add(sa);
        }

        scenario.setConditions(scenarioConditions);
        scenario.setActions(scenarioActions);

        scenarioRepository.save(scenario);   // ← ЕДИНСТВЕННОЕ сохранение, ПОСЛЕ заполнения

        log.info("Сохранён сценарий {} для хаба {} (conditions={}, actions={})",
                event.getName(), hubId, scenarioConditions.size(), scenarioActions.size());
    }

    private void removeScenario(String hubId, ScenarioRemovedEventAvro event) {
        scenarioRepository.findByHubIdAndName(hubId, event.getName())
                .ifPresent(scenarioRepository::delete);
        log.info("Удалён сценарий {} для хаба {}", event.getName(), hubId);
    }

    private Integer extractValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Boolean b) return b ? 1 : 0;
        return null;
    }
}