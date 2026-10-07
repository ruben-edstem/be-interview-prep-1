package com.mock.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID PRODUCT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache productCache;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository, productRepository, new OrderMapper(), transactionTemplate, cacheManager);
    }

    @Test
    void placeEvictsEveryOrderedProductFromTheProductCache() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(any(UUID.class), anyLong())).thenReturn(1);
        when(cacheManager.getCache("products")).thenReturn(productCache);

        orderService.place("key-1", request(item(PRODUCT_A, 1), item(PRODUCT_B, 1)));

        verify(productCache).evict(PRODUCT_A);
        verify(productCache).evict(PRODUCT_B);
    }

    @Test
    void placeLeavesTheProductCacheAloneWhenStockIsShort() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(PRODUCT_A, 5)).thenReturn(0);
        when(productRepository.findById(PRODUCT_A))
                .thenReturn(Optional.of(Product.builder().name("Widget").stock(2).build()));

        assertThrows(InsufficientStockException.class,
                () -> orderService.place("key-1", request(item(PRODUCT_A, 5))));

        verifyNoInteractions(cacheManager);
    }

    @Test
    void placeEvictsOnlyAfterTheTransactionCommits() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(any(UUID.class), anyLong())).thenReturn(1);
        when(cacheManager.getCache("products")).thenReturn(productCache);
        TransactionSynchronizationManager.initSynchronization();

        try {
            orderService.place("key-1", request(item(PRODUCT_A, 1)));

            verifyNoInteractions(productCache);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(productCache).evict(PRODUCT_A);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cancelEvictsEveryReturnedProductFromTheProductCache() {
        CustomerOrder order = storedOrder(request(item(PRODUCT_A, 4), item(PRODUCT_B, 1)));
        when(orderRepository.markCancelled(ORDER_ID)).thenReturn(1);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));
        when(cacheManager.getCache("products")).thenReturn(productCache);

        orderService.cancel(ORDER_ID);

        verify(productCache).evict(PRODUCT_A);
        verify(productCache).evict(PRODUCT_B);
    }

    @Test
    void cancelOfAnAlreadyCancelledOrderLeavesTheProductCacheAlone() {
        CustomerOrder order = storedOrder(request(item(PRODUCT_A, 4)));
        when(orderRepository.markCancelled(ORDER_ID)).thenReturn(0);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancel(ORDER_ID);

        verifyNoInteractions(cacheManager);
    }

    @Test
    void placeReservesStockForEveryItemInProductIdOrder() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(any(UUID.class), anyLong())).thenReturn(1);
        OrderRequest request = request(item(PRODUCT_B, 2), item(PRODUCT_A, 3));

        OrderPlacement placement = orderService.place("key-1", request);

        InOrder reservations = inOrder(productRepository);
        reservations.verify(productRepository).reserveStock(PRODUCT_A, 3);
        reservations.verify(productRepository).reserveStock(PRODUCT_B, 2);
        assertThat(placement.replayed()).isFalse();
        assertThat(placement.order().status()).isEqualTo(OrderStatus.PLACED);
        assertThat(placement.order().items()).hasSize(2);
    }

    @Test
    void placeAddsUpTheQuantitiesOfRepeatedProducts() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(any(UUID.class), anyLong())).thenReturn(1);
        OrderRequest request = request(item(PRODUCT_A, 2), item(PRODUCT_A, 3));

        OrderPlacement placement = orderService.place("key-1", request);

        verify(productRepository).reserveStock(PRODUCT_A, 5);
        assertThat(placement.order().items()).hasSize(1);
        assertThat(placement.order().items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    void placeSavesTheOrderBeforeReservingStock() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(any(UUID.class), anyLong())).thenReturn(1);

        orderService.place("key-1", request(item(PRODUCT_A, 1)));

        InOrder sequence = inOrder(orderRepository, productRepository);
        sequence.verify(orderRepository).saveAndFlush(any(CustomerOrder.class));
        sequence.verify(productRepository).reserveStock(PRODUCT_A, 1);
    }

    @Test
    void placeFailsWithInsufficientStockAndReportsWhatIsAvailable() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(PRODUCT_A, 5)).thenReturn(0);
        when(productRepository.findById(PRODUCT_A))
                .thenReturn(Optional.of(Product.builder().name("Widget").stock(2).build()));

        InsufficientStockException thrown = assertThrows(InsufficientStockException.class,
                () -> orderService.place("key-1", request(item(PRODUCT_A, 5))));

        assertThat(thrown.getMessage()).contains(PRODUCT_A.toString(), "requested 5", "available 2");
    }

    @Test
    void placeFailsForAnUnknownProduct() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(PRODUCT_A, 1)).thenReturn(0);
        when(productRepository.findById(PRODUCT_A)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> orderService.place("key-1", request(item(PRODUCT_A, 1))));
    }

    @Test
    void placeReplaysTheStoredOrderForARepeatedRequest() {
        CustomerOrder stored = storedOrder(request(item(PRODUCT_A, 2)));
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(stored));

        OrderPlacement placement = orderService.place("key-1", request(item(PRODUCT_A, 2)));

        assertThat(placement.replayed()).isTrue();
        assertThat(placement.order().id()).isEqualTo(ORDER_ID);
        verify(transactionTemplate, never()).execute(any());
        verify(productRepository, never()).reserveStock(any(UUID.class), anyLong());
    }

    @Test
    void placeTreatsTheSameItemsInAnyOrderAsTheSameRequest() {
        CustomerOrder stored = storedOrder(request(item(PRODUCT_A, 2), item(PRODUCT_B, 1)));
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(stored));

        OrderPlacement placement = orderService.place("key-1", request(item(PRODUCT_B, 1), item(PRODUCT_A, 2)));

        assertThat(placement.replayed()).isTrue();
    }

    @Test
    void placeRejectsAKeyReusedWithADifferentRequest() {
        CustomerOrder stored = storedOrder(request(item(PRODUCT_A, 2)));
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(stored));

        assertThrows(IdempotencyKeyReuseException.class,
                () -> orderService.place("key-1", request(item(PRODUCT_A, 3))));
    }

    @Test
    void placeReplaysTheWinnerWhenAConcurrentRetryInsertsFirst() {
        CustomerOrder winner = storedOrder(request(item(PRODUCT_A, 2)));
        when(orderRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(transactionTemplate.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        OrderPlacement placement = orderService.place("key-1", request(item(PRODUCT_A, 2)));

        assertThat(placement.replayed()).isTrue();
        assertThat(placement.order().id()).isEqualTo(ORDER_ID);
    }

    @Test
    void placeRethrowsAnIntegrityViolationThatIsNotADuplicateKey() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other constraint");
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(transactionTemplate.execute(any())).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> orderService.place("key-1", request(item(PRODUCT_A, 1))));

        assertThat(thrown).isSameAs(failure);
    }

    @Test
    void placeRejectsABlankIdempotencyKey() {
        InvalidRequestParameterException thrown = assertThrows(InvalidRequestParameterException.class,
                () -> orderService.place("  ", request(item(PRODUCT_A, 1))));

        assertThat(thrown.getParameter()).isEqualTo("Idempotency-Key");
    }

    @Test
    void placeRejectsAnIdempotencyKeyOver100Characters() {
        assertThrows(InvalidRequestParameterException.class,
                () -> orderService.place("k".repeat(101), request(item(PRODUCT_A, 1))));
    }

    @Test
    void getReturnsTheOrder() {
        when(orderRepository.findWithItemsById(ORDER_ID))
                .thenReturn(Optional.of(storedOrder(request(item(PRODUCT_A, 2)))));

        OrderResponse response = orderService.get(ORDER_ID);

        assertThat(response.id()).isEqualTo(ORDER_ID);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getFailsForAnUnknownOrder() {
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.get(ORDER_ID));
    }

    @Test
    void cancelReturnsTheStockOfEveryItemInProductIdOrder() {
        CustomerOrder order = storedOrder(request(item(PRODUCT_B, 1), item(PRODUCT_A, 4)));
        when(orderRepository.markCancelled(ORDER_ID)).thenReturn(1);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancel(ORDER_ID);

        InOrder releases = inOrder(productRepository);
        releases.verify(productRepository).releaseStock(PRODUCT_A, 4);
        releases.verify(productRepository).releaseStock(PRODUCT_B, 1);
    }

    @Test
    void cancelOfAnAlreadyCancelledOrderReturnsNoStockAgain() {
        CustomerOrder order = storedOrder(request(item(PRODUCT_A, 4)));
        when(orderRepository.markCancelled(ORDER_ID)).thenReturn(0);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(ORDER_ID);

        assertThat(response.id()).isEqualTo(ORDER_ID);
        verify(productRepository, never()).releaseStock(any(UUID.class), anyLong());
    }

    @Test
    void cancelFailsForAnUnknownOrder() {
        when(orderRepository.markCancelled(ORDER_ID)).thenReturn(0);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.cancel(ORDER_ID));
        verify(productRepository, never()).releaseStock(any(UUID.class), anyLong());
    }

    @Test
    void placeStoresTheOrderWithItsKeyAndPlacedStatus() {
        runTransactionCallbacks();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(productRepository.reserveStock(any(UUID.class), anyLong())).thenReturn(1);
        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);

        orderService.place("key-1", request(item(PRODUCT_A, 1)));

        verify(orderRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("key-1");
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(saved.getValue().getRequestFingerprint()).hasSize(64);
    }

    @SuppressWarnings("unchecked")
    private void runTransactionCallbacks() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
    }

    private CustomerOrder storedOrder(OrderRequest request) {
        Map<UUID, Long> quantities = new TreeMap<>();
        request.items().forEach(item -> quantities.merge(item.productId(), item.quantity(), Long::sum));
        CustomerOrder order = CustomerOrder.builder()
                .id(ORDER_ID)
                .idempotencyKey("key-1")
                .requestFingerprint(RequestFingerprint.of(quantities))
                .status(OrderStatus.PLACED)
                .build();
        quantities.forEach((productId, quantity) -> order.getItems().add(
                OrderItem.builder().productId(productId).quantity(quantity).build()));
        return order;
    }

    private OrderRequest request(OrderItemRequest... items) {
        return new OrderRequest(List.of(items));
    }

    private OrderItemRequest item(UUID productId, long quantity) {
        return new OrderItemRequest(productId, quantity);
    }
}
