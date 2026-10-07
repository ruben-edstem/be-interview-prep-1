package com.mock.taskmanager.service;

import com.mock.taskmanager.dto.request.ProductFilter;
import com.mock.taskmanager.dto.request.ProductRequest;
import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.exception.InvalidRequestParameterException;
import com.mock.taskmanager.exception.ProductNotFoundException;
import com.mock.taskmanager.mapper.ProductMapper;
import com.mock.taskmanager.repository.ProductRepository;
import com.mock.taskmanager.repository.ProductSpecifications;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .category(request.category())
                .price(request.price())
                .stock(request.stock())
                .rating(request.rating())
                .createdAt(Instant.now())
                .build();
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(ProductFilter filter, Pageable pageable) {
        validate(filter, pageable);
        Page<Product> products = productRepository.findAll(
                ProductSpecifications.matching(filter), withStableOrder(pageable));
        return products.map(productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return productMapper.toResponse(find(id));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = find(id);
        product.setName(request.name());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setRating(request.rating());
        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(UUID id) {
        productRepository.delete(find(id));
    }

    private Product find(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    private void validate(ProductFilter filter, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestParameterException("size", "must be at most " + MAX_PAGE_SIZE);
        }
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidRequestParameterException("page", "page is too large for the requested size");
        }
        if (filter.minPrice() != null && filter.minPrice() < 0) {
            throw new InvalidRequestParameterException("minPrice", "cannot be negative");
        }
        if (filter.maxPrice() != null && filter.maxPrice() < 0) {
            throw new InvalidRequestParameterException("maxPrice", "cannot be negative");
        }
        if (filter.minPrice() != null && filter.maxPrice() != null && filter.minPrice() > filter.maxPrice()) {
            throw new InvalidRequestParameterException("minPrice", "cannot be greater than maxPrice");
        }
    }

    private Pageable withStableOrder(Pageable pageable) {
        if (pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by("id")));
    }
}
