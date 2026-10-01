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
import ru.yandex.practicum.order.mapper.OrderMapper;

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
    private final OrderMapper orderMapper;

    public OrderDto createOrder(CreateOrderRequest request) {
        boolean allDataProvided = request.items().stream()
                .allMatch(i -> i.productName() != null && i.price() != null);

        if (allDataProvided) {
            return createSimpleOrder(request);
        }
        return createOrchestratedOrder(request);
    }

    private OrderDto createSimpleOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("CREATED");

        for (OrderItemRequest itemRequest : request.items()) {
            OrderItem item = orderMapper.toOrderItem(order, itemRequest);
            order.getItems().add(item);
        }

        order.setTotalPrice(orderMapper.calculateTotalPrice(order));

        return orderService.saveOrder(order);
    }

    public OrderDto createOrchestratedOrder(CreateOrderRequest request) {
        Map<Long, ServiceCallResult<ProductDto>> productResults = fetchAndValidateProducts(request);

        boolean anyDegraded = hasDegradation(productResults);
        anyDegraded = reserveStock(request) || anyDegraded;

        Order order = buildOrder(request, productResults, anyDegraded);

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

    private Map<Long, ServiceCallResult<ProductDto>> fetchAndValidateProducts(CreateOrderRequest request) {
        Map<Long, ServiceCallResult<ProductDto>> productResults = new HashMap<>();
        for (OrderItemRequest item : request.items()) {
            productResults.computeIfAbsent(item.productId(), this::fetchProduct);
        }

        for (ServiceCallResult<ProductDto> result : productResults.values()) {
            if (result instanceof ServiceCallResult.Failure<ProductDto> failure) {
                throw new OrderProcessingException(failure.message());
            }
        }

        for (ServiceCallResult<ProductDto> result : productResults.values()) {
            if (result instanceof ServiceCallResult.Success<ProductDto> success) {
                ProductDto product = success.value();
                if (!Boolean.TRUE.equals(product.active())) {
                    throw new OrderProcessingException(
                            "Товар с id=" + product.id() + " снят с продажи");
                }
            }
        }

        return productResults;
    }

    private boolean hasDegradation(Map<Long, ServiceCallResult<ProductDto>> productResults) {
        for (ServiceCallResult<ProductDto> result : productResults.values()) {
            if (result instanceof ServiceCallResult.Degraded<ProductDto>) {
                return true;
            }
        }
        return false;
    }

    private boolean reserveStock(CreateOrderRequest request) {
        Map<Long, Integer> totalQuantityByProduct = new HashMap<>();
        for (OrderItemRequest item : request.items()) {
            totalQuantityByProduct.merge(item.productId(), item.quantity(), Integer::sum);
        }

        List<ReserveRequest> reserved = new ArrayList<>();
        boolean anyDegraded = false;

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
        return anyDegraded;
    }

    private Order buildOrder(CreateOrderRequest request,
                             Map<Long, ServiceCallResult<ProductDto>> productResults,
                             boolean anyDegraded) {
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setCreatedAt(LocalDateTime.now());

        for (OrderItemRequest itemRequest : request.items()) {
            ProductDto product = null;
            ServiceCallResult<ProductDto> result = productResults.get(itemRequest.productId());
            if (result instanceof ServiceCallResult.Success<ProductDto> success) {
                product = success.value();
            }

            OrderItem item = orderMapper.toOrderItem(order, itemRequest, product);
            order.getItems().add(item);
        }

        order.setTotalPrice(orderMapper.calculateTotalPrice(order));

        if (anyDegraded) {
            order.setStatus("PENDING_CONFIRMATION");
            order.setStatusDetails("Заказ требует ручной проверки: часть данных недоступна");
        } else {
            order.setStatus("CONFIRMED");
            order.setStatusDetails(null);
        }

        return order;
    }
}