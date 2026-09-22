package ru.yandex.practicum.product.mapper;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.product.dto.CategoryDto;
import ru.yandex.practicum.product.dto.ProductDto;
import ru.yandex.practicum.product.entity.Category;
import ru.yandex.practicum.product.entity.Product;

@Component
public class ProductMapper {

    public CategoryDto toCategoryDto(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }

    public ProductDto toProductDto(Product product) {
        if (product == null) {
            return null;
        }
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                toCategoryDto(product.getCategory()),
                product.getImageUrl(),
                product.getActive()
        );
    }
}