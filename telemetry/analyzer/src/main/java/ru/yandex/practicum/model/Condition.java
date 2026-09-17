package ru.yandex.practicum.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "conditions")
@Getter
@Setter
public class Condition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ConditionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation")
    private ConditionOperation operation;

    @Column(name = "value")
    private Integer value;
}