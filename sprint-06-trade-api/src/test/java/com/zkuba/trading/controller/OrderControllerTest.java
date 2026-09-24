package com.zkuba.trading.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zkuba.trading.dto.response.OrderResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.security.JwtService;
import com.zkuba.trading.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setup() {

        AuthenticatedUser user =
                new AuthenticatedUser(
                        10L,
                        "username",
                        "CUSTOMER"
                );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("CUSTOMER")
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    // PLACE ORDER
    // =========================================================

    @Test
    void place_shouldReturnCreatedOrder_whenRequestIsValid()
            throws Exception {

        String requestJson = """
                {
                    "accountId": 123,
                    "symbol": "AAPL",
                    "side": "BUY",
                    "orderType": "LIMIT",
                    "quantity": 10,
                    "limitPrice": 150.00,
                    "idempotencyKey": "IDEMP-123"
                }
                """;

        OrderResponse response =
                new OrderResponse(
                        "ORD-123",
                        "NEW",
                        "AAPL",
                        "BUY",
                        "LIMIT",
                        10,
                        new BigDecimal("150.00"),
                        null,
                        "Order placed successfully"
                );

        when(orderService.place(eq(10L), any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.orderId")
                                .value("ORD-123")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("NEW")
                )
                .andExpect(
                        jsonPath("$.symbol")
                                .value("AAPL")
                )
                .andExpect(
                        jsonPath("$.side")
                                .value("BUY")
                )
                .andExpect(
                        jsonPath("$.orderType")
                                .value("LIMIT")
                )
                .andExpect(
                        jsonPath("$.quantity")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.limitPrice")
                                .value(150.00)
                )
                .andExpect(
                        jsonPath("$.executedPrice")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Order placed successfully")
                );

        verify(orderService).place(eq(10L), any());
    }

    @Test
    void place_shouldReturn422_whenRequestIsInvalid()
            throws Exception {

        String requestJson = """
                {
                    "accountId": null,
                    "symbol": "",
                    "side": null,
                    "orderType": null,
                    "quantity": 0,
                    "limitPrice": 150.00,
                    "idempotencyKey": ""
                }
                """;

        mockMvc.perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                );

        verify(orderService, never())
                .place(any(Long.class), any());
    }

    // =========================================================
    // UPDATE ORDER
    // =========================================================

    @Test
    void update_shouldReturnUpdatedOrder_whenRequestIsValid()
            throws Exception {

        UUID orderId = UUID.randomUUID();

        String requestJson = """
                {
                    "quantity": 20,
                    "limitPrice": 155.00
                }
                """;

        OrderResponse response =
                new OrderResponse(
                        "ORD-456",
                        "NEW",
                        "AAPL",
                        "BUY",
                        "LIMIT",
                        20,
                        new BigDecimal("155.00"),
                        null,
                        "Order updated successfully"
                );

        when(orderService.update(
                eq(10L),
                eq(orderId),
                any()
        )).thenReturn(response);

        mockMvc.perform(
                        put("/api/v1/orders/{id}", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.orderId")
                                .value("ORD-456")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("NEW")
                )
                .andExpect(
                        jsonPath("$.symbol")
                                .value("AAPL")
                )
                .andExpect(
                        jsonPath("$.quantity")
                                .value(20)
                )
                .andExpect(
                        jsonPath("$.limitPrice")
                                .value(155.00)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Order updated successfully")
                );

        verify(orderService).update(
                eq(10L),
                eq(orderId),
                any()
        );
    }

    @Test
    void update_shouldReturn422_whenOrderIdHasOrdPrefix()
            throws Exception {

        String requestJson = """
                {
                    "quantity": 20,
                    "limitPrice": 155.00
                }
                """;

        mockMvc.perform(
                        put("/api/v1/orders/ORD-456")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Order ID must be a raw UUID without 'ORD-' prefix: ORD-456"
                                )
                );

        verify(orderService, never())
                .update(any(Long.class), any(UUID.class), any());
    }

    @Test
    void update_shouldReturn422_whenOrderIdIsInvalidUuid()
            throws Exception {

        String requestJson = """
                {
                    "quantity": 20,
                    "limitPrice": 155.00
                }
                """;

        mockMvc.perform(
                        put("/api/v1/orders/not-a-uuid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Invalid order ID format: not-a-uuid"
                                )
                );

        verify(orderService, never())
                .update(any(Long.class), any(UUID.class), any());
    }

    @Test
    void update_shouldReturn422_whenQuantityIsInvalid()
            throws Exception {

        UUID orderId = UUID.randomUUID();

        String requestJson = """
                {
                    "quantity": 0,
                    "limitPrice": 155.00
                }
                """;

        mockMvc.perform(
                        put("/api/v1/orders/{id}", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                );

        verify(orderService, never())
                .update(any(Long.class), any(UUID.class), any());
    }

    // =========================================================
    // CANCEL - POST
    // =========================================================

    @Test
    void cancelPost_shouldReturnCancelledOrder_whenIdIsValid()
            throws Exception {

        UUID orderId = UUID.randomUUID();

        OrderResponse response =
                new OrderResponse(
                        "ORD-789",
                        "CANCELLED",
                        "AAPL",
                        "BUY",
                        "LIMIT",
                        10,
                        new BigDecimal("150.00"),
                        null,
                        "Order cancelled successfully"
                );

        when(orderService.cancel(
                eq(10L),
                eq(orderId)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/orders/{id}/cancel", orderId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.orderId")
                                .value("ORD-789")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("CANCELLED")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Order cancelled successfully")
                );

        verify(orderService).cancel(
                eq(10L),
                eq(orderId)
        );
    }

    @Test
    void cancelPost_shouldReturn422_whenIdHasOrdPrefix()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/orders/ORD-789/cancel")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Order ID must be a raw UUID without 'ORD-' prefix: ORD-789"
                                )
                );

        verify(orderService, never())
                .cancel(any(Long.class), any(UUID.class));
    }

    @Test
    void cancelPost_shouldReturn422_whenIdIsInvalidUuid()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/orders/not-a-uuid/cancel")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Invalid order ID format: not-a-uuid"
                                )
                );

        verify(orderService, never())
                .cancel(any(Long.class), any(UUID.class));
    }

}
