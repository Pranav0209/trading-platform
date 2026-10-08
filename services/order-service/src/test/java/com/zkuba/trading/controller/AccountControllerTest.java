package com.zkuba.trading.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zkuba.trading.domain.enums.OrderStatus;
import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.dto.response.BalanceResponse;
import com.zkuba.trading.dto.response.OrderHistoryEntry;
import com.zkuba.trading.dto.response.PortfolioResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.security.JwtService;
import com.zkuba.trading.service.AccountService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AccountController.class, com.zkuba.trading.exception.GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    /*
     * JwtAuthenticationFilter needs JwtService while the
     * Spring test context is being created.
     *
     * The JWT filter itself is disabled for these controller tests.
     */
    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setUpAuthentication() {

        AuthenticatedUser user =
                new AuthenticatedUser(10L, "username", "CUSTOMER");

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // GET /api/v1/accounts/{id}
    // ============================================================

    @Test
    void getAccount_shouldReturnAccount() throws Exception {

        Long accountId = 123L;
        Long userId = 10L;

        AccountResponse response =
                new AccountResponse(
                        123L,
                        "ACC-123",
                        "John Doe",
                        new BigDecimal("10000.00"),
                        "ACTIVE",
                        1,
                        "2026-09-10T06:30:00Z"
                );

        when(accountService.getAccountResponseById(
                accountId,
                userId
        )).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/accounts/{id}", accountId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.accountId").value("ACC-123"))
                .andExpect(jsonPath("$.holderName").value("John Doe"))
                .andExpect(jsonPath("$.cashBalance").value(10000.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lastUpdated")
                        .value("2026-09-10T06:30:00Z"));

        verify(accountService)
                .getAccountResponseById(accountId, userId);
    }

    // ============================================================
    // GET /api/v1/accounts/{id}/balance
    // ============================================================

    @Test
    void getBalance_shouldReturnBalance() throws Exception {

        Long accountId = 123L;
        Long userId = 10L;

        /*
         * Replace these constructor arguments with the actual
         * BalanceResponse constructor from your project if needed.
         */
        BalanceResponse response = null;

        when(accountService.getBalance(
                accountId,
                userId
        )).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/accounts/{id}/balance", accountId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk());

        verify(accountService)
                .getBalance(accountId, userId);
    }

    // ============================================================
    // GET /api/v1/accounts/{id}/positions
    // ============================================================

    @Test
    void getPortfolio_shouldReturnPositions() throws Exception {

        Long accountId = 123L;
        Long userId = 10L;

        List<PortfolioResponse> response = List.of();

        when(accountService.getPortfolioResponse(
                accountId,
                userId
        )).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/accounts/{id}/portfolio", accountId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(accountService)
                .getPortfolioResponse(accountId, userId);
    }

    // ============================================================
    // GET /api/v1/accounts/{id}/orders
    // ============================================================

    @Test
    void getOrders_shouldReturnOrders() throws Exception {

        Long accountId = 123L;
        Long userId = 10L;

        List<OrderHistoryEntry> response = List.of();

        when(accountService.getOrderHistory(
                accountId,
                null,
                null,
                null,
                userId
        )).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/accounts/{id}/orders", accountId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(accountService)
                .getOrderHistory(
                        accountId,
                        null,
                        null,
                        null,
                        userId
                );
    }

    // ============================================================
    // GET /accounts/{id}/orders with filters
    // ============================================================

    @Test
    void getOrders_shouldPassFiltersToService() throws Exception {

        Long accountId = 123L;
        Long userId = 10L;

        OrderStatus status = OrderStatus.FILLED;

        OffsetDateTime from =
                OffsetDateTime.parse("2026-09-01T00:00:00Z");

        OffsetDateTime to =
                OffsetDateTime.parse("2026-09-10T00:00:00Z");

        List<OrderHistoryEntry> response = List.of();

        when(accountService.getOrderHistory(
                accountId,
                status,
                from,
                to,
                userId
        )).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/accounts/{id}/orders", accountId)
                                .param("status", status.name())
                                .param("from", from.toString())
                                .param("to", to.toString())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(accountService)
                .getOrderHistory(
                        accountId,
                        status,
                        from,
                        to,
                        userId
                );
    }
}