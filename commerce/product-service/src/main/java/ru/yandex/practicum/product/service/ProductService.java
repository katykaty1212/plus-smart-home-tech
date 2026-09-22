package ru.yandex.practicum.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.product.dto.CreateProductRequest;
import ru.yandex.practicum.product.dto.ProductDto;
import ru.yandex.practicum.product.dto.UpdateProductRequest;
import ru.yandex.practicum.product.entity.Category;
import ru.yandex.practicum.product.entity.Product;
import ru.yandex.practicum.product.exception.NotFoundException;
import ru.yandex.practicum.product.mapper.ProductMapper;
import ru.yandex.practicum.product.repository.CategoryRepository;
import ru.yandex.practicum.product.repository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public List<ProductDto> getAll() {
        return productRepository.findAllByActiveTrue().stream()
                .map(productMapper::toProductDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Товар с id=" + id + " не найден"));
        return productMapper.toProductDto(product);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getByCategory(Long categoryId) {
        return productRepository.findAllByActiveTrueAndCategoryId(categoryId).stream()
                .map(productMapper::toProductDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDto> search(String query) {
        return productRepository.findAllByActiveTrueAndNameContainingIgnoreCase(query).stream()
                .map(productMapper::toProductDto)
                .toList();
    }

    @Transactional
    public ProductDto create(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setImageUrl(request.imageUrl());
        product.setActive(true);

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException(
                            "Категория с id=" + request.categoryId() + " не найдена"));
            product.setCategory(category);
        }

        productRepository.save(product);
        return productMapper.toProductDto(product);
    }

    @Transactional
    public ProductDto update(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Товар с id=" + id + " не найден"));

        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException(
                            "Категория с id=" + request.categoryId() + " не найдена"));
            product.setCategory(category);
        }

        return productMapper.toProductDto(product);
    }
}