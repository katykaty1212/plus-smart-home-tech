package ru.yandex.practicum.order.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;
import ru.yandex.practicum.order.feign.InventoryClient;
import ru.yandex.practicum.order.feign.ProductClient;
import ru.yandex.practicum.order.feign.ProductDto;
import ru.yandex.practicum.order.feign.ReserveRequest;
import ru.yandex.practicum.order.feign.ReserveResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOrchestrationService {

    private final OrderService orderService;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    public OrderDto createOrder(CreateOrderRequest request) {
        // 1. Получаем товары (кэш на время запроса)
        Map<Long, ServiceCallResult<ProductDto>> productResults = new HashMap<>();
        for (OrderItemRequest item : request.items()) {
            productResults.computeIfAbsent(item.productId(), this::fetchProduct);
        }

        // 2. Проверяем бизнес-отказы по товарам
        for (ServiceCallResult<ProductDto> result : productResults.values()) {
            if (result instanceof ServiceCallResult.Failure<ProductDto> failure) {
                throw new OrderProcessingException(failure.message());
            }
        }

        // 2.5. Проверяем деградацию по товарам — выставляем флаг
        boolean anyDegraded = false;
        for (ServiceCallResult<ProductDto> result : productResults.values()) {
            if (result instanceof ServiceCallResult.Degraded<ProductDto>) {
                anyDegraded = true;
                break;
            }
        }

        // 3. Проверяем active у успешно полученных товаров
        for (ServiceCallResult<ProductDto> result : productResults.values()) {
            if (result instanceof ServiceCallResult.Success<ProductDto> success) {
                ProductDto product = success.value();
                if (!Boolean.TRUE.equals(product.active())) {
                    throw new OrderProcessingException(
                            "Товар с id=" + product.id() + " снят с продажи");
                }
            }
        }

        // 4. Группируем по productId — суммарное количество
        Map<Long, Integer> totalQuantityByProduct = new HashMap<>();
        for (OrderItemRequest item : request.items()) {
            totalQuantityByProduct.merge(item.productId(), item.quantity(), Integer::sum);
        }

        // 5. Резервируем
        List<ReserveRequest> reserved = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : totalQuantityByProduct.entrySet()) {
            ReserveRequest reserveRequest = new ReserveRequest(entry.getKey(), entry.getValue());
            ServiceCallResult<ReserveResponse> result = reserveStock(reserveRequest);

            if (result instanceof ServiceCallResult.Failure<ReserveResponse> failure) {
                compensateReserved(reserved);
                throw new OrderProcessingException(failure.message());
            }

            if (result instanceof ServiceCallResult.Degraded<ReserveResponse>) {
                anyDegraded = true;
                continue;
            }

            if (result instanceof ServiceCallResult.Success<ReserveResponse> success) {
                reserved.add(reserveRequest);
                if (!success.value().success()) {
                    compensateReserved(reserved);
                    throw new OrderProcessingException(
                            "Недостаточно товара id=" + entry.getKey() + " на складе");
                }
            }
        }

        // 6. Собираем Order
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setCreatedAt(LocalDateTime.now());

        if (anyDegraded) {
            order.setStatus("PENDING_CONFIRMATION");
            order.setStatusDetails("Заказ требует ручной проверки: часть данных недоступна");
        } else {
            order.setStatus("CONFIRMED");
            order.setStatusDetails(null);
        }

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            ServiceCallResult<ProductDto> result = productResults.get(itemRequest.productId());

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(itemRequest.productId());
            item.setQuantity(itemRequest.quantity());

            if (result instanceof ServiceCallResult.Success<ProductDto> success) {
                ProductDto product = success.value();
                item.setProductName(product.name());
                item.setPrice(product.price());
                totalPrice = totalPrice.add(
                        product.price().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            } else {
                item.setProductName("Товар #" + itemRequest.productId() + " (ожидает проверки)");
                item.setPrice(BigDecimal.ZERO);
            }

            order.getItems().add(item);
        }

        order.setTotalPrice(totalPrice);

        return orderService.saveOrder(order);
    }

    private ServiceCallResult<ProductDto> fetchProduct(Long productId) {
        try {
            ProductDto product = productClient.getProductById(productId);
            return new ServiceCallResult.Success<>(product);
        } catch (ProductServiceUnavailableException e) {
            return new ServiceCallResult.Degraded<>("Каталог временно недоступен");
        } catch (FeignException.NotFound e) {
            return new ServiceCallResult.Failure<>("Товар с id=" + productId + " не найден");
        } catch (FeignException e) {
            return new ServiceCallResult.Failure<>(
                    "Не удалось получить данные товара id=" + productId);
        }
    }

    private ServiceCallResult<ReserveResponse> reserveStock(ReserveRequest request) {
        try {
            ReserveResponse response = inventoryClient.reserveStock(request);
            return new ServiceCallResult.Success<>(response);
        } catch (InventoryServiceUnavailableException e) {
            return new ServiceCallResult.Degraded<>("Склад временно недоступен");
        } catch (FeignException.NotFound e) {
            return new ServiceCallResult.Failure<>(
                    "Складская запись для товара id=" + request.productId() + " не найдена");
        } catch (FeignException.Conflict e) {
            return new ServiceCallResult.Failure<>(
                    "Недостаточно товара id=" + request.productId() + " на складе");
        } catch (FeignException e) {
            return new ServiceCallResult.Failure<>(
                    "Не удалось зарезервировать товар id=" + request.productId());
        }
    }

    private void compensateReserved(List<ReserveRequest> reserved) {
        for (ReserveRequest r : reserved) {
            try {
                inventoryClient.releaseStock(r);
            } catch (Exception ex) {
                log.error("Не удалось снять резерв {}: {}", r, ex.getMessage());
            }
        }
    }
}