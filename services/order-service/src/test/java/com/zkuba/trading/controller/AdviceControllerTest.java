package com.zkuba.trading.controller;

import com.zkuba.trading.dto.response.AdviceResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter;
import com.zkuba.trading.service.AdviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AdviceControllerTest {

    @Mock
    private AdviceService adviceService;

    @InjectMocks
    private AdviceController adviceController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAdviceSuccess() {
        JwtAuthenticationFilter.AuthenticatedUser user = new JwtAuthenticationFilter.AuthenticatedUser(1L, "user1", "CUSTOMER");
        AdviceResponse mockResponse = new AdviceResponse(1L, Instant.now(), Collections.emptyList());
        
        when(adviceService.getAdvice(1L, 1L)).thenReturn(mockResponse);

        // JWT token might have accountId=1 but we pass it as path variable, which should match
        // Wait, accountId is from path, user has userId=1
        // Currently the test simulates passing 1L as accountId. We don't check accountId == userId in controller anymore!
        // We removed that check from the controller and moved it to the service.
        
        ResponseEntity<AdviceResponse> response = adviceController.getAdvice(1L, user);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(mockResponse, response.getBody());
    }
}
