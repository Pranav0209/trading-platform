package com.zkuba.trading.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zkuba.trading.dto.response.AccountResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.security.JwtService;
import com.zkuba.trading.service.AccountService;
import java.math.BigDecimal;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import com.zkuba.trading.exception.GlobalExceptionHandler;

@WebMvcTest({AdminAccountController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class AdminAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setup() {

        AuthenticatedUser user =
                new AuthenticatedUser(
                        10L,
                        "username",
                        "ADMIN"
                );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ADMIN")
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
    // SUCCESS
    // =========================================================

    @Test
    void create_shouldReturnAccount_whenRequestIsValid()
            throws Exception {

        String requestJson = """
                {
                    "userId": 10,
                    "currency": "USD",
                    "initialBalance": 1000.00
                }
                """;

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

        when(accountService.create(any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/admin/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(
                        jsonPath("$.accountId")
                                .value("ACC-123")
                )
                .andExpect(
                        jsonPath("$.holderName")
                                .value("John Doe")
                )
                .andExpect(
                        jsonPath("$.cashBalance")
                                .value(10000.00)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                )
                .andExpect(
                        jsonPath("$.version")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.lastUpdated")
                                .value("2026-09-10T06:30:00Z")
                );

        verify(accountService).create(any());
    }

    // =========================================================
    // VALIDATION FAILURE - userId missing
    // =========================================================

    @Test
    void create_shouldReturn422_whenUserIdIsNull()
            throws Exception {

        String requestJson = """
                {
                    "userId": null,
                    "currency": "USD"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/admin/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                )
                .andExpect(jsonPath("$.message").isString());

        verify(accountService, org.mockito.Mockito.never())
                .create(any());
    }

    // =========================================================
    // VALIDATION FAILURE - currency invalid
    // =========================================================

    @Test
    void create_shouldReturn422_whenCurrencyIsInvalid()
            throws Exception {

        String requestJson = """
                {
                    "userId": 10,
                    "currency": "usd"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/admin/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                )
                .andExpect(jsonPath("$.message").isString());

        verify(accountService, org.mockito.Mockito.never())
                .create(any());
    }

    // =========================================================
    // VALIDATION FAILURE - both fields invalid
    // =========================================================

    @Test
    void create_shouldReturn422_whenRequestIsInvalid()
            throws Exception {

        String requestJson = """
                {
                    "userId": null,
                    "currency": "usd"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/admin/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.error")
                                .value("VAL-422")
                )
                .andExpect(jsonPath("$.message").isString());

        verify(accountService, org.mockito.Mockito.never())
                .create(any());
    }
}
