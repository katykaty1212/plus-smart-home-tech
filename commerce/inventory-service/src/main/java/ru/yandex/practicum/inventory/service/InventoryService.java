package ru.yandex.practicum.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.inventory.dto.InventoryDto;
import ru.yandex.practicum.inventory.dto.ReserveRequest;
import ru.yandex.practicum.inventory.dto.ReserveResponse;
import ru.yandex.practicum.inventory.dto.UpdateInventoryRequest;
import ru.yandex.practicum.inventory.entity.Inventory;
import ru.yandex.practicum.inventory.exception.InsufficientStockException;
import ru.yandex.practicum.inventory.exception.NotFoundException;
import ru.yandex.practicum.inventory.mapper.InventoryMapper;
import ru.yandex.practicum.inventory.repository.InventoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;

    @Transactional(readOnly = true)
    public List<InventoryDto> getAll() {
        return inventoryRepository.findAll().stream()
                .map(inventoryMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryDto getByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException(
                        "Складская запись для товара " + productId + " не найдена"));
        return inventoryMapper.toDto(inventory);
    }

    @Transactional
    public InventoryDto create(UpdateInventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.productId())) {
            throw new IllegalArgumentException(
                    "Складская запись для товара " + request.productId() + " уже существует");
        }

        Inventory inventory = inventoryMapper.toEntity(request);

        inventoryRepository.save(inventory);
        return inventoryMapper.toDto(inventory);
    }

    @Transactional
    public InventoryDto update(UpdateInventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.productId())
                .orElseThrow(() -> new NotFoundException(
                        "Складская запись для товара " + request.productId() + " не найдена"));

        inventory.setQuantity(request.quantity());
        return inventoryMapper.toDto(inventory);
    }

    @Transactional
    public ReserveResponse reserve(ReserveRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.productId())
                .orElseThrow(() -> new NotFoundException(
                        "Складская запись для товара " + request.productId() + " не найдена"));

        int available = inventory.getQuantity() - inventory.getReservedQuantity();

        if (available < request.quantity()) {
            throw new InsufficientStockException(
                    "Недостаточно товара " + request.productId() +
                            ": доступно " + available + ", запрошено " + request.quantity());
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + request.quantity());

        Inventory saved = inventoryRepository.save(inventory);

        return new ReserveResponse(
                true,
                saved.getAvailableQuantity(),
                "Товар успешно зарезервирован");
    }
}