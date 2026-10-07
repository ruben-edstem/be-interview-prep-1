package com.mock.taskmanager.service;

import com.mock.taskmanager.dto.request.OrderItemRequest;
import com.mock.taskmanager.dto.request.OrderRequest;
import com.mock.taskmanager.dto.response.OrderResponse;
import com.mock.taskmanager.entity.CustomerOrder;
import com.mock.taskmanager.entity.OrderItem;
import com.mock.taskmanager.entity.OrderStatus;
import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.exception.IdempotencyKeyReuseException;
import com.mock.taskmanager.exception.InsufficientStockException;
import com.mock.taskmanager.exception.InvalidRequestParameterException;
import com.mock.taskmanager.exception.OrderNotFoundException;
import com.mock.taskmanager.exception.ProductNotFoundException;
import com.mock.taskmanager.mapper.OrderMapper;
import com.mock.taskmanager.repository.OrderRepository;
import com.mock.taskmanager.repository.ProductRepository;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 100;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final TransactionTemplate transactionTemplate;

    public OrderPlacement place(String idempotencyKey, OrderRequest request) {
        requireValidKey(idempotencyKey);
        Map<UUID, Long> quantities = totalQuantities(request);
        String fingerprint = RequestFingerprint.of(quantities);

        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> replay(existing, fingerprint))
                .orElseGet(() -> create(idempotencyKey, quantities, fingerprint));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return orderMapper.toResponse(find(id));
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        int cancelled = orderRepository.markCancelled(id);
        CustomerOrder order = find(id);
        if (cancelled == 1) {
            order.getItems().stream()
                    .sorted(Comparator.comparing(OrderItem::getProductId))
                    .forEach(item -> productRepository.releaseStock(item.getProductId(), item.getQuantity()));
        }
        return orderMapper.toResponse(order);
    }

    private OrderPlacement create(String idempotencyKey, Map<UUID, Long> quantities, String fingerprint) {
        try {
            CustomerOrder order = transactionTemplate.execute(
                    status -> reserveAndSave(idempotencyKey, quantities, fingerprint));
            return new OrderPlacement(orderMapper.toResponse(order), false);
        } catch (DataIntegrityViolationException ex) {
            CustomerOrder winner = orderRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            return replay(winner, fingerprint);
        }
    }

    private CustomerOrder reserveAndSave(String idempotencyKey, Map<UUID, Long> quantities, String fingerprint) {
        CustomerOrder order = CustomerOrder.builder()
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .status(OrderStatus.PLACED)
                .build();
        quantities.forEach((productId, quantity) -> order.getItems().add(
                OrderItem.builder().productId(productId).quantity(quantity).build()));
        orderRepository.saveAndFlush(order);

        quantities.forEach(this::reserve);
        return order;
    }

    private void reserve(UUID productId, long quantity) {
        if (productRepository.reserveStock(productId, quantity) == 0) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            throw new InsufficientStockException(productId, quantity, product.getStock());
        }
    }

    private OrderPlacement replay(CustomerOrder existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReuseException();
        }
        return new OrderPlacement(orderMapper.toResponse(existing), true);
    }

    private CustomerOrder find(UUID id) {
        return orderRepository.findWithItemsById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private void requireValidKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestParameterException(IDEMPOTENCY_KEY, "must not be blank");
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestParameterException(IDEMPOTENCY_KEY,
                    "must be at most " + MAX_KEY_LENGTH + " characters");
        }
    }

    private Map<UUID, Long> totalQuantities(OrderRequest request) {
        return request.items().stream()
                .collect(Collectors.toMap(
                        OrderItemRequest::productId,
                        OrderItemRequest::quantity,
                        Long::sum,
                        TreeMap::new));
    }
}
