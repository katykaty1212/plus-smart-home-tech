package ru.yandex.practicum.order.exception;

import lombok.Getter;

@Getter
public class InventoryServiceUnavailableException extends RuntimeException {

    private final Long productId;

    public InventoryServiceUnavailableException(Long productId, Throwable cause) {
        super("Сервис склада временно недоступен при обработке товара id=" + productId, cause);
        this.productId = productId;
    }
}