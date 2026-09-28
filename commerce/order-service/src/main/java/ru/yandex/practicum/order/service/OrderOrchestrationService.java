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
import ru.yandex.practicum.order.exception.OrderProcessingException;
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
        // 1. Получаем товары (по одному разу на productId)
        Map<Long, ProductDto> productsById = new HashMap<>();
        for (OrderItemRequest item : request.items()) {
            productsById.computeIfAbsent(item.productId(), this::fetchProduct);
        }

        // 2. Проверяем активность
        for (ProductDto product : productsById.values()) {
            if (!Boolean.TRUE.equals(product.active())) {
                throw new OrderProcessingException(
                        "Товар с id=" + product.id() + " снят с продажи");
            }
        }

        // 3. Группируем позиции по productId — суммарное количество
        Map<Long, Integer> totalQuantityByProduct = new HashMap<>();
        for (OrderItemRequest item : request.items()) {
            totalQuantityByProduct.merge(item.productId(), item.quantity(), Integer::sum);
        }

        // 4. Резервируем — храним успешные резервы для компенсации
        List<ReserveRequest> reserved = new ArrayList<>();
        try {
            for (Map.Entry<Long, Integer> entry : totalQuantityByProduct.entrySet()) {
                ReserveRequest reserveRequest = new ReserveRequest(entry.getKey(), entry.getValue());
                ReserveResponse response = reserveStock(reserveRequest);
                if (response.success()) {
                    reserved.add(reserveRequest);
                }
            }
        } catch (RuntimeException e) {
            // 5. Компенсация — снимаем уже сделанные резервы
            for (ReserveRequest r : reserved) {
                try {
                    inventoryClient.releaseStock(r);
                } catch (Exception ex) {
                    log.error("Не удалось снять резерв {}: {}", r, ex.getMessage());
                }
            }
            throw e;
        }

        // 6. Собираем Order (без сохранения)
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setStatus("CONFIRMED");
        order.setStatusDetails(null);
        order.setCreatedAt(LocalDateTime.now());

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            ProductDto product = productsById.get(itemRequest.productId());

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.id());
            item.setProductName(product.name());
            item.setQuantity(itemRequest.quantity());
            item.setPrice(product.price());
            order.getItems().add(item);

            BigDecimal lineTotal = product.price()
                    .multiply(BigDecimal.valueOf(itemRequest.quantity()));
            totalPrice = totalPrice.add(lineTotal);
        }

        order.setTotalPrice(totalPrice);

        // 7. Сохранение — через OrderService, транзакционно
        return orderService.saveOrder(order);
    }

    private ProductDto fetchProduct(Long productId) {
        try {
            return productClient.getProductById(productId);
        } catch (FeignException.NotFound e) {
            throw new OrderProcessingException("Товар с id=" + productId + " не найден");
        } catch (FeignException e) {
            throw new OrderProcessingException(
                    "Не удалось получить данные товара id=" + productId);
        }
    }

    private ReserveResponse reserveStock(ReserveRequest request) {
        try {
            return inventoryClient.reserveStock(request);
        } catch (FeignException.NotFound e) {
            throw new OrderProcessingException(
                    "Складская запись для товара id=" + request.productId() + " не найдена");
        } catch (FeignException.Conflict e) {
            throw new OrderProcessingException(
                    "Недостаточно товара id=" + request.productId() + " на складе");
        } catch (FeignException e) {
            throw new OrderProcessingException(
                    "Не удалось зарезервировать товар id=" + request.productId());
        }
    }
}