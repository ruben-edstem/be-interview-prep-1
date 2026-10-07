package com.mock.taskmanager.repository;

import com.mock.taskmanager.entity.CustomerOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithItemsById(UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerOrder o set o.status = com.mock.taskmanager.entity.OrderStatus.CANCELLED "
            + "where o.id = :id and o.status = com.mock.taskmanager.entity.OrderStatus.PLACED")
    int markCancelled(@Param("id") UUID id);
}
