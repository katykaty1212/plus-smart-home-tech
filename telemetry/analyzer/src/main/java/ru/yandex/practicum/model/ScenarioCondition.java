package ru.yandex.practicum.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "scenario_conditions")
@IdClass(ScenarioCondition.Pk.class)
@Getter
@Setter
public class ScenarioCondition {

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
    @JoinColumn(name = "condition_id")
    private Condition condition;

    public static class Pk implements Serializable {
        private Scenario scenario;
        private Sensor sensor;
        private Condition condition;

        public Pk() {}

        public Pk(Scenario scenario, Sensor sensor, Condition condition) {
            this.scenario = scenario;
            this.sensor = sensor;
            this.condition = condition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(scenario, pk.scenario)
                    && Objects.equals(sensor, pk.sensor)
                    && Objects.equals(condition, pk.condition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scenario, sensor, condition);
        }
    }
}