package com.mock.taskmanager.mapper;

import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getStock());
    }
}
