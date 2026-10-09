package com.zkuba.trading.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.dto.response.RealisedPnlHistoryResponse;
import com.zkuba.trading.dto.response.RealisedPnlRecord;
import com.zkuba.trading.dto.response.RealisedPnlSummary;
import com.zkuba.trading.exception.ForbiddenException;
import com.zkuba.trading.exception.InvalidParametersException;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.RealisedPnlService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.zkuba.trading.security.JwtService;
import com.zkuba.trading.exception.GlobalExceptionHandler;

@WebMvcTest({PortfolioController.class, GlobalExceptionHandler.class})
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RealisedPnlService realisedPnlService;

    @MockBean
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    // ==============================================
    // Success Cases
    // ==============================================

    @BeforeEach
    void setUp() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "user1", "USER");
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testGetRealisedPnlHistory_Success() throws Exception {
        // Given: Service returns valid response
        Long accountId = 1L;
        Long userId = 1L;
        
        RealisedPnlRecord record = new RealisedPnlRecord(
                1L,
                UUID.randomUUID(),
                "AAPL",
                100,
                new BigDecimal("150.00"),
                new BigDecimal("100.00"),
                new BigDecimal("5000.00"),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        
        RealisedPnlSummary summary = new RealisedPnlSummary(
                1L, 1L, 0L, 20L, new BigDecimal("5000.00")
        );
        
        RealisedPnlHistoryResponse response = new RealisedPnlHistoryResponse(
                accountId,
                "USD",
                null,
                null,
                List.of(record),
                summary,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), eq(userId), any(), any(), any(), any(), any()
        )).thenReturn(response);

        // When/Then: GET endpoint should return 200 with response
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(accountId))
        .andExpect(jsonPath("$.baseCurrency").value("USD"))
        .andExpect(jsonPath("$.records.length()").value(1))
        .andExpect(jsonPath("$.records[0].symbol").value("AAPL"))
        .andExpect(jsonPath("$.records[0].quantity").value(100))
        .andExpect(jsonPath("$.summary.totalCount").value(1))
        .andExpect(jsonPath("$.summary.totalPages").value(1));
    }

    @Test
    void testGetRealisedPnlHistory_WithFilters() throws Exception {
        // Given: Service receives symbol, from, to, page, size
        Long accountId = 1L;
        Long userId = 1L;
        String symbol = "AAPL";
        LocalDate from = LocalDate.of(2026, 10, 01);
        LocalDate to = LocalDate.of(2026, 10, 31);
        
        RealisedPnlSummary summary = new RealisedPnlSummary(
                0L, 0L, 0L, 20L, BigDecimal.ZERO
        );
        
        RealisedPnlHistoryResponse response = new RealisedPnlHistoryResponse(
                accountId,
                "USD",
                from,
                to,
                List.of(),
                summary,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), eq(userId), eq(symbol), eq(from), eq(to), eq(1), eq(50)
        )).thenReturn(response);

        // When/Then: All filter parameters should be passed to service
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .param("symbol", symbol)
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31")
                        .param("page", "1")
                        .param("size", "50")
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records.length()").value(0));

        verify(realisedPnlService).getRealisedPnlHistory(
                accountId, userId, symbol, from, to, 1, 50
        );
    }

    @Test
    void testGetRealisedPnlHistory_DefaultPagination() throws Exception {
        // Given: Service returns default page (0, 20)
        Long accountId = 1L;
        Long userId = 1L;
        
        RealisedPnlSummary summary = new RealisedPnlSummary(
                20L, 1L, 0L, 20L, new BigDecimal("10000.00")
        );
        
        RealisedPnlHistoryResponse response = new RealisedPnlHistoryResponse(
                accountId,
                "USD",
                null,
                null,
                List.of(),
                summary,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), eq(userId), any(), any(), any(), isNull(), isNull()
        )).thenReturn(response);

        // When/Then: Omitting page and size should use defaults (0, 20)
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.currentPage").value(0))
        .andExpect(jsonPath("$.summary.pageSize").value(20));
    }

    // ==============================================
    // Error Cases: JWT/Authorization
    // ==============================================

    @Test
    void testGetRealisedPnlHistory_Forbidden_AccountMismatch() throws Exception {
        // Given: JWT user 1 requesting account 2 (doesn't belong to them)
        Long accountId = 2L;
        Long jwtUserId = 1L;
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), eq(jwtUserId), any(), any(), any(), any(), any()
        )).thenThrow(new ForbiddenException("PNL-403", "Account does not belong to token user"));

        // When/Then: Should return 403 Forbidden
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isForbidden());
    }

    @Test
    void testGetRealisedPnlHistory_Unauthorized_NoJwt() throws Exception {
        SecurityContextHolder.clearContext();
        // Given: Request without JWT
        Long accountId = 1L;

        // When/Then: Should return 401 Unauthorized
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isUnauthorized());
    }

    // ==============================================
    // Error Cases: Invalid Parameters
    // ==============================================

    @Test
    void testGetRealisedPnlHistory_BadRequest_InvalidDateRange() throws Exception {
        // Given: from > to
        Long accountId = 1L;
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), any(), any(), any(), any(), any(), any()
        )).thenThrow(new InvalidParametersException("VAL-422", "From date must not be after to date"));

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .param("from", "2026-10-31")
                        .param("to", "2026-10-01")
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void testGetRealisedPnlHistory_BadRequest_InvalidPageSize() throws Exception {
        // Given: pageSize > 100
        Long accountId = 1L;
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), any(), any(), any(), any(), any(), eq(101)
        )).thenThrow(new InvalidParametersException("VAL-422", "Page size must be between 1 and 100"));

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .param("size", "101")
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void testGetRealisedPnlHistory_BadRequest_NegativePage() throws Exception {
        // Given: page < 0
        Long accountId = 1L;
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), any(), any(), any(), any(), eq(-1), any()
        )).thenThrow(new InvalidParametersException("VAL-422", "Page must be >= 0"));

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .param("page", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest());
    }

    // ==============================================
    // Error Cases: Not Found
    // ==============================================

    @Test
    void testGetRealisedPnlHistory_NotFound_AccountDoesNotExist() throws Exception {
        // Given: accountId doesn't exist
        Long accountId = 99999L;
        Long userId = 1L;
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), eq(userId), any(), any(), any(), any(), any()
        )).thenThrow(new RuntimeException("Account not found")); // Would be AccountNotFoundException

        // When/Then: Should return 500 (or map to 404 with global exception handler)
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().is5xxServerError());
    }

    // ==============================================
    // Response Format Tests
    // ==============================================

    @Test
    void testGetRealisedPnlHistory_ResponseIncludesAllFields() throws Exception {
        // Given: Full response with all fields
        Long accountId = 1L;
        LocalDate from = LocalDate.of(2026, 10, 01);
        LocalDate to = LocalDate.of(2026, 10, 19);
        
        RealisedPnlRecord record = new RealisedPnlRecord(
                1L,
                UUID.randomUUID(),
                "AAPL",
                100,
                new BigDecimal("150.5000"),
                new BigDecimal("100.2000"),
                new BigDecimal("5030.00"),
                OffsetDateTime.of(2026, 10, 19, 14, 30, 0, 0, ZoneOffset.UTC)
        );
        
        RealisedPnlSummary summary = new RealisedPnlSummary(
                1L, 1L, 0L, 20L, new BigDecimal("5030.00")
        );
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RealisedPnlHistoryResponse response = new RealisedPnlHistoryResponse(
                accountId,
                "USD",
                from,
                to,
                List.of(record),
                summary,
                now
        );
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), any(), any(), eq(from), eq(to), any(), any()
        )).thenReturn(response);

        // When/Then: Response should have all fields
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-19")
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(accountId))
        .andExpect(jsonPath("$.baseCurrency").value("USD"))
        .andExpect(jsonPath("$.from").value("2026-10-01"))
        .andExpect(jsonPath("$.to").value("2026-10-19"))
        .andExpect(jsonPath("$.records[0].pnlId").value(1))
        .andExpect(jsonPath("$.records[0].symbol").value("AAPL"))
        .andExpect(jsonPath("$.records[0].quantity").value(100))
        .andExpect(jsonPath("$.records[0].sellPrice").value(150.5))
        .andExpect(jsonPath("$.records[0].averageCost").value(100.2))
        .andExpect(jsonPath("$.records[0].realisedPnl").value(5030.0))
        .andExpect(jsonPath("$.summary.totalCount").value(1))
        .andExpect(jsonPath("$.summary.totalPages").value(1))
        .andExpect(jsonPath("$.summary.currentPage").value(0))
        .andExpect(jsonPath("$.summary.pageSize").value(20))
        .andExpect(jsonPath("$.summary.totalRealisedPnl").value(5030.0))
        .andExpect(jsonPath("$.asOf").exists());
    }

    @Test
    void testGetRealisedPnlHistory_EmptyResults() throws Exception {
        // Given: No P&L records for account
        Long accountId = 1L;
        
        RealisedPnlSummary summary = new RealisedPnlSummary(
                0L, 0L, 0L, 20L, BigDecimal.ZERO
        );
        
        RealisedPnlHistoryResponse response = new RealisedPnlHistoryResponse(
                accountId,
                "USD",
                null,
                null,
                List.of(),
                summary,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        
        when(realisedPnlService.getRealisedPnlHistory(
                eq(accountId), any(), any(), any(), any(), any(), any()
        )).thenReturn(response);

        // When/Then: Response should have empty records and zero P&L
        mockMvc.perform(
                get("/api/v1/portfolio/{accountId}/pnl/realised", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records.length()").value(0))
        .andExpect(jsonPath("$.summary.totalCount").value(0))
        .andExpect(jsonPath("$.summary.totalRealisedPnl").value("0"));
    }
}
