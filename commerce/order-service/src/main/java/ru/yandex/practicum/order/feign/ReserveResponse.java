package ru.yandex.practicum.order.feign;

public record ReserveResponse(
        boolean success,
        Integer availableQuantity,
        String message
) {
}