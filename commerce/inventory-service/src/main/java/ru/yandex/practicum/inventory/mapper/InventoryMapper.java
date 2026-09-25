package ru.yandex.practicum.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.yandex.practicum.inventory.dto.InventoryDto;
import ru.yandex.practicum.inventory.dto.UpdateInventoryRequest;
import ru.yandex.practicum.inventory.entity.Inventory;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "availableQuantity",
            expression = "java(inventory.getQuantity() - inventory.getReservedQuantity())")
    InventoryDto toDto(Inventory inventory);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reservedQuantity", constant = "0")
    @Mapping(target = "version", ignore = true)
    Inventory toEntity(UpdateInventoryRequest request);
}