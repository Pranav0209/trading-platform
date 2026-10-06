package com.zkuba.trading.mapper;

import com.zkuba.trading.domain.entity.Order;
import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.domain.enums.OrderType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderMapperIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private OrderMapper orderMapper;

    @Test
    @DisplayName("inserted order can be retrieved by account and id")
    void insertedOrderCanBeRetrievedById() {

        Order order = newOrder(
                2L,
                "mapper-test-key-1",
                OrderStatus.NEW);

        int inserted = orderMapper.insert(order);

        Order found = orderMapper.findByIdAndAccountId(
                order.getId(),
                2L);

        assertThat(inserted).isEqualTo(1);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(order.getId());
        assertThat(found.getAccountId()).isEqualTo(2L);
        assertThat(found.getSymbol()).isEqualTo("AAPL");
        assertThat(found.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(found.getOrderType()).isEqualTo(OrderType.MARKET);
        assertThat(found.getQuantity()).isEqualTo(1);
        assertThat(found.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(found.getIdempotencyKey())
                .isEqualTo("mapper-test-key-1");
    }

    @Test
    @DisplayName("conditional cancel returns 1 when status matches and 0 when it does not")
    void conditionalStatusUpdateReturnsCorrectRowCount() {

        Order order = newOrder(
                2L,
                "mapper-test-key-2",
                OrderStatus.NEW);

        int inserted = orderMapper.insert(order);

        assertThat(inserted).isEqualTo(1);

        int firstUpdate = orderMapper.cancelOrderConditionally(
                order.getId(),
                2L,
                OrderStatus.NEW.name());

        int secondUpdate = orderMapper.cancelOrderConditionally(
                order.getId(),
                2L,
                OrderStatus.NEW.name());

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(secondUpdate).isEqualTo(0);
    }


    @Test
    @DisplayName("constraint violations are propagated")
    void constraintViolationIsPropagated() {

        Order order = newOrder(
                999999L,
                "mapper-test-key-3",
                OrderStatus.NEW);

        assertThatThrownBy(() -> orderMapper.insert(order))
                .isInstanceOf(RuntimeException.class);
    }

    private static Order newOrder(
            Long accountId,
            String idempotencyKey,
            OrderStatus status) {

        Order order = new Order();

        order.setId(UUID.randomUUID());
        order.setAccountId(accountId);
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.MARKET);
        order.setQuantity(1);
        order.setLimitPrice(null);
        order.setExecutedPrice(new BigDecimal("100.00"));
        order.setStatus(status);
        order.setIdempotencyKey(idempotencyKey);
        order.setCreatedAt(OffsetDateTime.now());

        return order;
    }
}
