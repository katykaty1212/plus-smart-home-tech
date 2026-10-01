package ru.yandex.practicum.order.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.feign.ProductDto;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderDto toDto(Order order);

    OrderItemDto toItemDto(OrderItem item);

    List<OrderItemDto> toItemDtoList(List<OrderItem> items);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "CREATED")
    @Mapping(target = "statusDetails", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "items", ignore = true)
    Order toEntity(CreateOrderRequest request);

    default OrderItem toOrderItem(Order order, OrderItemRequest request) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(request.productId());
        item.setProductName(request.productName());
        item.setQuantity(request.quantity());
        item.setPrice(request.price());
        return item;
    }

    default OrderItem toOrderItem(Order order, OrderItemRequest request, ProductDto product) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(request.productId());
        item.setQuantity(request.quantity());

        if (product != null) {
            item.setProductName(product.name());
            item.setPrice(product.price());
        } else {
            item.setProductName("Товар #" + request.productId() + " (ожидает проверки)");
            item.setPrice(BigDecimal.ZERO);
        }

        return item;
    }

    default BigDecimal calculateTotalPrice(Order order) {
        return order.getItems().stream()
                .map(item -> item.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}