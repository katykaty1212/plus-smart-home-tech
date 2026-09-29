package ru.yandex.practicum.order.feign;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;

@Slf4j
@Component
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        return new InventoryClient() {

            @Override
            public ReserveResponse reserveStock(ReserveRequest request) {
                if (cause instanceof FeignException.NotFound notFound) {
                    throw notFound;
                }
                if (cause instanceof FeignException.Conflict conflict) {
                    throw conflict;
                }
                log.warn("inventory-service недоступен при резервировании товара id={}",
                        request.productId(), cause);
                throw new InventoryServiceUnavailableException(request.productId(), cause);
            }

            @Override
            public ReserveResponse releaseStock(ReserveRequest request) {
                log.warn("inventory-service недоступен при снятии резерва товара id={}",
                        request.productId(), cause);
                throw new InventoryServiceUnavailableException(request.productId(), cause);
            }
        };
    }
}