package ru.yandex.practicum.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "scenario_actions")
@IdClass(ScenarioAction.Pk.class)
@Getter
@Setter
public class ScenarioAction {

    @Id
    @ManyToOne
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @Id
    @ManyToOne
    @JoinColumn(name = "sensor_id")
    private Sensor sensor;

    @Id
    @ManyToOne
    @JoinColumn(name = "action_id")
    private Action action;

    public static class Pk implements Serializable {
        private Long scenario;
        private String sensor;
        private Long action;

        public Pk() {}

        public Pk(Long scenario, String sensor, Long action) {
            this.scenario = scenario;
            this.sensor = sensor;
            this.action = action;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(scenario, pk.scenario)
                    && Objects.equals(sensor, pk.sensor)
                    && Objects.equals(action, pk.action);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scenario, sensor, action);
        }
    }
}