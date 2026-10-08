package com.zkuba.trading.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.domain.entity.PriceAlert.AlertState;
import com.zkuba.trading.dto.request.CreateAlertRequest;
import com.zkuba.trading.dto.response.AlertResponse;
import com.zkuba.trading.exception.GlobalExceptionHandler;
import com.zkuba.trading.security.JwtAuthenticationFilter;
import com.zkuba.trading.service.PriceAlertService;
import com.zkuba.trading.service.AccountService;
import com.zkuba.trading.domain.entity.Account;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.zkuba.trading.security.JwtService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import com.zkuba.trading.exception.ForbiddenException;

@WebMvcTest(controllers = {PriceAlertController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class PriceAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PriceAlertService alertService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        JwtAuthenticationFilter.AuthenticatedUser user = new JwtAuthenticationFilter.AuthenticatedUser(1L, "user@example.com", "ROLE_USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void getAlerts_Success() throws Exception {
        AlertResponse alertResponse = new AlertResponse(
                10L, 1L, "AAPL", "Apple Inc.", "ABOVE", new BigDecimal("150.00"),
                "ACTIVE", null, "PENDING", null, OffsetDateTime.now()
        );

        when(alertService.getAlerts(1L, null)).thenReturn(List.of(alertResponse));
        when(accountService.getAccountAndValidateAccess(1L, 1L)).thenReturn(new Account());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/alerts", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].id").value(10L));
    }

    @Test
    void getAlerts_WithStateFilter() throws Exception {
        AlertResponse alertResponse = new AlertResponse(
                10L, 1L, "AAPL", "Apple Inc.", "ABOVE", new BigDecimal("150.00"),
                "TRIGGERED", null, "DELIVERED", null, OffsetDateTime.now()
        );

        when(alertService.getAlerts(1L, AlertState.TRIGGERED)).thenReturn(List.of(alertResponse));
        when(accountService.getAccountAndValidateAccess(1L, 1L)).thenReturn(new Account());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/alerts", 1L)
                        .param("state", "TRIGGERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].state").value("TRIGGERED"));
    }
    
    @Test
    void getAlerts_InvalidStateFilter() throws Exception {
        when(accountService.getAccountAndValidateAccess(1L, 1L)).thenReturn(new Account());
        mockMvc.perform(get("/api/v1/accounts/{accountId}/alerts", 1L)
                        .param("state", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAlerts_Forbidden() throws Exception {
        doThrow(new ForbiddenException("ACC-403", "Account mismatch"))
            .when(accountService).getAccountAndValidateAccess(2L, 1L);
        
        mockMvc.perform(get("/api/v1/accounts/{accountId}/alerts", 2L))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAlert_Success() throws Exception {
        CreateAlertRequest request = new CreateAlertRequest("AAPL", "ABOVE", new BigDecimal("150.00"));
        AlertResponse alertResponse = new AlertResponse(
                10L, 1L, "AAPL", "Apple Inc.", "ABOVE", new BigDecimal("150.00"),
                "ACTIVE", null, "PENDING", null, OffsetDateTime.now()
        );

        when(alertService.createAlert(eq(1L), any(CreateAlertRequest.class))).thenReturn(alertResponse);
        when(accountService.getAccountAndValidateAccess(1L, 1L)).thenReturn(new Account());

        mockMvc.perform(post("/api/v1/accounts/{accountId}/alerts", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L));
    }

    @Test
    void getAlert_Success() throws Exception {
        AlertResponse alertResponse = new AlertResponse(
                10L, 1L, "AAPL", "Apple Inc.", "ABOVE", new BigDecimal("150.00"),
                "ACTIVE", null, "PENDING", null, OffsetDateTime.now()
        );

        when(alertService.getAlert(1L, 10L)).thenReturn(alertResponse);
        when(accountService.getAccountAndValidateAccess(1L, 1L)).thenReturn(new Account());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/alerts/{alertId}", 1L, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));
    }

    @Test
    void deleteAlert_Success() throws Exception {
        when(accountService.getAccountAndValidateAccess(1L, 1L)).thenReturn(new Account());
        mockMvc.perform(delete("/api/v1/accounts/{accountId}/alerts/{alertId}", 1L, 10L))
                .andExpect(status().isNoContent());

        verify(alertService).deleteAlert(1L, 10L);
    }

    @Test
    void resetAlert_Success() throws Exception {
        AlertResponse alertResponse = new AlertResponse(
                10L, 1L, "AAPL", "Apple Inc.", "ABOVE", new BigDecimal("150.00"),
                "ACTIVE", null, "PENDING", null, OffsetDateTime.now()
        );

        when(alertService.resetAlert(1L, 10L)).thenReturn(alertResponse);
        when(accountService.getAccountAndValidateAccess(1L, 1L)).thenReturn(new Account());

        mockMvc.perform(post("/api/v1/accounts/{accountId}/alerts/{alertId}/reset", 1L, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));
    }
}
