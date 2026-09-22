package ru.yandex.practicum.inventory.mapper;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.inventory.dto.InventoryDto;
import ru.yandex.practicum.inventory.entity.Inventory;

@Component
public class InventoryMapper {

    public InventoryDto toDto(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        int available = inventory.getQuantity() - inventory.getReservedQuantity();
        return new InventoryDto(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                available
        );
    }
}