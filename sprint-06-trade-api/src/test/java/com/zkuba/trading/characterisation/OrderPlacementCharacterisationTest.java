package com.zkuba.trading.characterisation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.domain.enums.OrderSide;
import com.zkuba.trading.domain.enums.OrderType;
import com.zkuba.trading.dto.request.PlaceOrderRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterisation tests pinning order placement behavior.
 * Focused on database state (the source of truth) using actual JDBC validation
 * via REST APIs.
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
public class OrderPlacementCharacterisationTest {

        @Container
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

        @DynamicPropertySource
        static void configureDatasource(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
        }

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
        private String jwtSecret;

        private String validToken(Long userId) {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Date now = new Date();
                return Jwts.builder()
                                .subject("testuser")
                                .claim("userId", userId)
                                .claim("roles", List.of("CUSTOMER"))
                                .issuedAt(now)
                                .expiration(new Date(now.getTime() + 3600000))
                                .signWith(key)
                                .compact();
        }

        @BeforeEach
        void setUp() {
                jdbcTemplate.execute("DELETE FROM orders");
                jdbcTemplate.execute("DELETE FROM positions");
                jdbcTemplate.execute("DELETE FROM accounts");
                jdbcTemplate.execute("DELETE FROM users");

                // Insert test users
                jdbcTemplate.update(
                                "INSERT INTO users (id, username, email, password_hash, role, status) VALUES (?, ?, ?, ?, ?, ?)",
                                1L, "testuser1", "test1@example.com", "hash", "CUSTOMER", "ACTIVE");
                jdbcTemplate.update(
                                "INSERT INTO users (id, username, email, password_hash, role, status) VALUES (?, ?, ?, ?, ?, ?)",
                                2L, "testuser2", "test2@example.com", "hash", "CUSTOMER", "ACTIVE");

                // Insert accounts
                jdbcTemplate.update(
                                "INSERT INTO accounts (id, account_number, user_id, cash_balance, currency, status) VALUES (?, ?, ?, ?, ?, ?)",
                                100L, "ACC-100", 1L, new BigDecimal("5000.00"), "USD", "ACTIVE");
                jdbcTemplate.update(
                                "INSERT INTO accounts (id, account_number, user_id, cash_balance, currency, status) VALUES (?, ?, ?, ?, ?, ?)",
                                200L, "ACC-200", 2L, new BigDecimal("5000.00"), "USD", "SUSPENDED");
        }

        @Test
        @DisplayName("Pin Sprint 7: Valid order placement persists to database with NEW status")
        void pinValidOrderPlacement() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                100L, "AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("150.00"),
                                "idem-key-100");

                mockMvc.perform(post("/api/v1/orders")
                                .header("Authorization", "Bearer " + validToken(1L))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.orderId", startsWith("ORD-")))
                                .andExpect(jsonPath("$.status", is("NEW")))
                                .andExpect(jsonPath("$.symbol", is("AAPL")))
                                .andExpect(jsonPath("$.side", is("BUY")))
                                .andExpect(jsonPath("$.orderType", is("LIMIT")))
                                .andExpect(jsonPath("$.quantity", is(10)))
                                .andExpect(jsonPath("$.limitPrice", is(150.0)))
                                .andExpect(jsonPath("$.message", is("Order placed")));

                // Verify DB State (the source of truth) using JDBC calls
                List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                                "SELECT * FROM orders WHERE idempotency_key = ?",
                                "idem-key-100");
                assertThat(orders).hasSize(1);

                Map<String, Object> orderRow = orders.get(0);
                assertThat(orderRow.get("account_id")).isEqualTo(100L);
                assertThat(orderRow.get("symbol")).isEqualTo("AAPL");
                assertThat(orderRow.get("side")).isEqualTo("BUY");
                assertThat(orderRow.get("order_type")).isEqualTo("LIMIT");
                assertThat(orderRow.get("quantity")).isEqualTo(10);
                assertThat(orderRow.get("status")).isEqualTo("NEW");
                assertThat(new BigDecimal(orderRow.get("limit_price").toString())).isEqualByComparingTo("150.00");
                assertThat(orderRow.get("executed_price")).isNull();
        }

        @Test
        @DisplayName("Pin: Reused idempotency key throws DuplicateOrderException (mapped to 409)")
        void pinReusedIdempotencyKeyThrowsDuplicateOrderException() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                100L, "AAPL", OrderSide.BUY, OrderType.LIMIT, 5, new BigDecimal("100.00"),
                                "reused-key");

                // First successful placement
                mockMvc.perform(post("/api/v1/orders")
                                .header("Authorization", "Bearer " + validToken(1L))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                // Second placement returns 409 Conflict
                mockMvc.perform(post("/api/v1/orders")
                                .header("Authorization", "Bearer " + validToken(1L))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error").value("ORD-409"))
                                .andExpect(jsonPath("$.message")
                                                .value("Duplicate order with idempotency key: reused-key"));

                // Verify only one order was written
                Integer count = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM orders WHERE idempotency_key = 'reused-key'",
                                Integer.class);
                assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Pin: Non-ACTIVE (SUSPENDED) account order placement throws AccountNotActiveException (mapped to 403)")
        void pinSuspendedAccountThrowsAccountNotActiveException() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                200L, "AAPL", OrderSide.BUY, OrderType.LIMIT, 1, new BigDecimal("100.00"),
                                "suspended-acc-key");

                mockMvc.perform(post("/api/v1/orders")
                                .header("Authorization", "Bearer " + validToken(2L))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error").value("ACC-403"))
                                .andExpect(jsonPath("$.message").value("Account not active: 200"));

                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE account_id = 200",
                                Integer.class);
                assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("Pin Sprint 7: Unaffordable BUY order is now accepted asynchronously as NEW")
        void pinUnaffordableBuyOrderIsNowAcceptedAsynchronously() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                100L, "AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("100.00"), // Needs
                                                                                                             // 10k,
                                                                                                             // account
                                                                                                             // has
                                                                                                             // 5k
                                "unaffordable-key");

                mockMvc.perform(post("/api/v1/orders")
                                .header("Authorization", "Bearer " + validToken(1L))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status", is("NEW")));

                // Verify DB State
                List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                                "SELECT * FROM orders WHERE idempotency_key = ?",
                                "unaffordable-key");
                assertThat(orders).hasSize(1);
                assertThat(orders.get(0).get("status")).isEqualTo("NEW");
        }

        @Test
        @DisplayName("Pin Sprint 7: Unknown symbol order causes DataIntegrityViolationException (mapped to 500 or 409)")
        void pinUnknownSymbolPropagatesDatabaseException() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                100L, "UNKNOWN_SYM", OrderSide.BUY, OrderType.LIMIT, 1, new BigDecimal("10.00"),
                                "unknown-sym-key");

                // The unknown symbol causes a foreign key constraint violation on the
                // instruments table.
                // Postgres aborts the transaction, which throws an exception mapping to
                // SYS-500.
                mockMvc.perform(post("/api/v1/orders")
                                .header("Authorization", "Bearer " + validToken(1L))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.error").value("SYS-500"));

                // Verify no order created
                Integer count = jdbcTemplate
                                .queryForObject("SELECT COUNT(*) FROM orders WHERE idempotency_key = 'unknown-sym-key'",
                                                Integer.class);
                assertThat(count).isEqualTo(0);
        }
}