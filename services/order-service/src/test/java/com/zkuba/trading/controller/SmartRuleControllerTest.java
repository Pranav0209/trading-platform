package com.zkuba.trading.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.domain.entity.SmartRule.ComparisonOperator;
import com.zkuba.trading.domain.entity.SmartRule.MetricType;
import com.zkuba.trading.domain.entity.SmartRule.QuantityMode;
import com.zkuba.trading.domain.entity.SmartRule.RuleAction;
import com.zkuba.trading.dto.request.CreateSmartRuleRequest;
import com.zkuba.trading.dto.response.SmartRuleResponse;
import com.zkuba.trading.exception.GlobalExceptionHandler;
import com.zkuba.trading.security.JwtAuthenticationFilter;
import com.zkuba.trading.service.SmartRuleService;
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

@WebMvcTest(controllers = {SmartRuleController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class SmartRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SmartRuleService smartRuleService;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtAuthenticationFilter.AuthenticatedUser user = new JwtAuthenticationFilter.AuthenticatedUser(1L, "user@example.com", "ROLE_USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void getRules_Success() throws Exception {
        SmartRuleResponse resp = new SmartRuleResponse(
                10L, "AAPL", "Apple Inc.", "BUY", "FIXED", 10,
                "PRICE", "LESS_THAN", new BigDecimal("150.00"),
                null, null, null, true, null, null, null, null, List.of()
        );

        when(smartRuleService.getRules(1L)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/smart-rules", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L));
    }

    @Test
    void getRules_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/smart-rules", 2L))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRule_Success() throws Exception {
        CreateSmartRuleRequest request = new CreateSmartRuleRequest(
                "AAPL", RuleAction.BUY, QuantityMode.FIXED, 10,
                MetricType.PRICE, ComparisonOperator.LESS_THAN, new BigDecimal("150.00"),
                null, null, null, true, null
        );

        SmartRuleResponse resp = new SmartRuleResponse(
                10L, "AAPL", "Apple Inc.", "BUY", "FIXED", 10,
                "PRICE", "LESS_THAN", new BigDecimal("150.00"),
                null, null, null, true, null, null, null, null, List.of()
        );

        when(smartRuleService.createRule(eq(1L), any(CreateSmartRuleRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/smart-rules", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L));
    }

    @Test
    void deleteRule_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{accountId}/smart-rules/{ruleId}", 1L, 10L))
                .andExpect(status().isNoContent());

        verify(smartRuleService).deleteRule(1L, 10L);
    }
}
