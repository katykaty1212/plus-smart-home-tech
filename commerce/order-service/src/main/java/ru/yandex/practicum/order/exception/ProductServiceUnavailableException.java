package ru.yandex.practicum.order.exception;

import lombok.Getter;

@Getter
public class ProductServiceUnavailableException extends RuntimeException {

    private final Long productId;

    public ProductServiceUnavailableException(Long productId, Throwable cause) {
        super("Сервис каталога временно недоступен при запросе товара id=" + productId, cause);
        this.productId = productId;
    }
}