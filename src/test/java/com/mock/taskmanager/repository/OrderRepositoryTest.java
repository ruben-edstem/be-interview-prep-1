package com.mock.taskmanager.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mock.taskmanager.entity.CustomerOrder;
import com.mock.taskmanager.entity.OrderItem;
import com.mock.taskmanager.entity.OrderStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savingAnOrderStoresItsItemsStatusAndCreatedDate() {
        UUID productId = UUID.randomUUID();
        CustomerOrder saved = orderRepository.saveAndFlush(order("key-1", productId, 2));
        entityManager.clear();

        CustomerOrder found = orderRepository.findWithItemsById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getItems()).extracting(OrderItem::getProductId).containsExactly(productId);
        assertThat(found.getItems()).extracting(OrderItem::getQuantity).containsExactly(2L);
    }

    @Test
    void findByIdempotencyKeyReturnsTheMatchingOrderWithItsItems() {
        orderRepository.saveAndFlush(order("key-1", UUID.randomUUID(), 1));
        CustomerOrder second = orderRepository.saveAndFlush(order("key-2", UUID.randomUUID(), 1));
        entityManager.clear();

        CustomerOrder found = orderRepository.findByIdempotencyKey("key-2").orElseThrow();

        assertThat(found.getId()).isEqualTo(second.getId());
        assertThat(found.getItems()).hasSize(1);
    }

    @Test
    void findByIdempotencyKeyIsEmptyForAnUnknownKey() {
        assertThat(orderRepository.findByIdempotencyKey("missing")).isEmpty();
    }

    @Test
    void anIdempotencyKeyCannotBeUsedByTwoOrders() {
        orderRepository.saveAndFlush(order("key-1", UUID.randomUUID(), 1));

        assertThrows(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(order("key-1", UUID.randomUUID(), 1)));
    }

    @Test
    void markCancelledChangesAPlacedOrderOnce() {
        CustomerOrder saved = orderRepository.saveAndFlush(order("key-1", UUID.randomUUID(), 1));

        int first = orderRepository.markCancelled(saved.getId());
        int second = orderRepository.markCancelled(saved.getId());

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(orderRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void markCancelledForAnUnknownOrderUpdatesNothing() {
        int updated = orderRepository.markCancelled(UUID.randomUUID());

        assertThat(updated).isZero();
    }

    private CustomerOrder order(String key, UUID productId, long quantity) {
        CustomerOrder order = CustomerOrder.builder()
                .idempotencyKey(key)
                .requestFingerprint("fingerprint")
                .status(OrderStatus.PLACED)
                .build();
        order.getItems().add(OrderItem.builder().productId(productId).quantity(quantity).build());
        return order;
    }
}
