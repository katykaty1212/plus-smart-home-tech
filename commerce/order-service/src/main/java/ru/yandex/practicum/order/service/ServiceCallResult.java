package ru.yandex.practicum.order.service;

public sealed interface ServiceCallResult<T>
        permits ServiceCallResult.Success,
        ServiceCallResult.Failure,
        ServiceCallResult.Degraded {

    record Success<T>(T value) implements ServiceCallResult<T> { }

    record Failure<T>(String message) implements ServiceCallResult<T> { }

    record Degraded<T>(String reason) implements ServiceCallResult<T> { }
}