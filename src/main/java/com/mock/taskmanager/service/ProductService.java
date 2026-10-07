package com.mock.taskmanager.service;

import com.mock.taskmanager.dto.request.ProductRequest;
import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.exception.ProductNotFoundException;
import com.mock.taskmanager.mapper.ProductMapper;
import com.mock.taskmanager.repository.ProductRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .stock(request.stock())
                .build();
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return productMapper.toResponse(product);
    }
}
