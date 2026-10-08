package com.zkuba.trading.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.UserRepository;
import com.zkuba.trading.service.NotificationService.OrderNotificationPayload;
import com.zkuba.trading.service.PriceAlertEvaluator.AlertNotificationPayload;
import com.zkuba.trading.service.impl.NotificationServiceImpl;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Account account;
    private User user;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(1L);
        account.setUserId(2L);

        user = new User();
        user.setId(2L);
        user.setEmail("test@example.com");
    }

    @Test
    void sendPriceAlertNotification_EmailChannel() {
        user.setNotificationChannel("EMAIL");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        AlertNotificationPayload payload = new AlertNotificationPayload(
                100L, 1L, "AAPL", "ABOVE", new BigDecimal("150"), new BigDecimal("151"), OffsetDateTime.now()
        );

        assertDoesNotThrow(() -> notificationService.sendPriceAlertNotification(payload));
        verify(restTemplate).postForEntity(any(String.class), any(HttpEntity.class), eq(String.class));
    }
    
    @Test
    void sendPriceAlertNotification_PushChannel() {
        user.setNotificationChannel("PUSH");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        AlertNotificationPayload payload = new AlertNotificationPayload(
                100L, 1L, "AAPL", "BELOW", new BigDecimal("150"), new BigDecimal("149"), OffsetDateTime.now()
        );

        assertDoesNotThrow(() -> notificationService.sendPriceAlertNotification(payload));
        verifyNoInteractions(restTemplate); // PUSH not implemented, should just log
    }

    @Test
    void sendPriceAlertNotification_AccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        AlertNotificationPayload payload = new AlertNotificationPayload(
                100L, 1L, "AAPL", "ABOVE", new BigDecimal("150"), new BigDecimal("151"), OffsetDateTime.now()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> notificationService.sendPriceAlertNotification(payload));
        assertEquals("Account not found", ex.getMessage());
    }
    
    @Test
    void sendPriceAlertNotification_UserNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        AlertNotificationPayload payload = new AlertNotificationPayload(
                100L, 1L, "AAPL", "ABOVE", new BigDecimal("150"), new BigDecimal("151"), OffsetDateTime.now()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> notificationService.sendPriceAlertNotification(payload));
        assertEquals("User not found", ex.getMessage());
    }
    
    @Test
    void sendOrderNotification_EmailChannel() {
        user.setNotificationChannel("EMAIL");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        OrderNotificationPayload payload = new OrderNotificationPayload(
                1L, "order-123", "BUY", 10, "AAPL", "FILLED"
        );

        assertDoesNotThrow(() -> notificationService.sendOrderNotification(payload));
        verify(restTemplate).postForEntity(any(String.class), any(HttpEntity.class), eq(String.class));
    }
    
    @Test
    void sendOrderNotification_PushChannel() {
        user.setNotificationChannel("PUSH");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        OrderNotificationPayload payload = new OrderNotificationPayload(
                1L, "order-123", "BUY", 10, "AAPL", "FILLED"
        );

        assertDoesNotThrow(() -> notificationService.sendOrderNotification(payload));
        verifyNoInteractions(restTemplate);
    }
    
    @Test
    void sendOrderNotification_AccountNotFound_IgnoresError() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        OrderNotificationPayload payload = new OrderNotificationPayload(
                1L, "order-123", "BUY", 10, "AAPL", "FILLED"
        );

        // Does not throw
        assertDoesNotThrow(() -> notificationService.sendOrderNotification(payload));
    }
    
    @Test
    void sendOrderNotification_UserNotFound_IgnoresError() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        OrderNotificationPayload payload = new OrderNotificationPayload(
                1L, "order-123", "BUY", 10, "AAPL", "FILLED"
        );

        assertDoesNotThrow(() -> notificationService.sendOrderNotification(payload));
    }
    
    @Test
    void sendPriceAlertNotification_ApiError() {
        user.setNotificationChannel("EMAIL");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("API down"));

        AlertNotificationPayload payload = new AlertNotificationPayload(
                100L, 1L, "AAPL", "ABOVE", new BigDecimal("150"), new BigDecimal("151"), OffsetDateTime.now()
        );

        assertThrows(RuntimeException.class, () -> notificationService.sendPriceAlertNotification(payload));
    }
}
