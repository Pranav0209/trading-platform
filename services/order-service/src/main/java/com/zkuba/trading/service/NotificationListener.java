package com.zkuba.trading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.event.Envelope;
import com.zkuba.trading.event.OrderPlacedPayload;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.zkuba.trading.event.TradeEventPayload;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${emailjs.service-id}")
    private String serviceId;

    @Value("${emailjs.template-id}")
    private String templateId;

    @Value("${emailjs.public-key}")
    private String publicKey;

    @Value("${emailjs.private-key}")
    private String privateKey;

    public NotificationListener(AccountRepository accountRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @KafkaListener(topics = "orders", groupId = "order-service-notifications-group")
    public void handleOrderEvent(String message) {
        try {
            Envelope<OrderPlacedPayload> envelope = objectMapper.readValue(message, new TypeReference<Envelope<OrderPlacedPayload>>() {});
            
            if ("ORDER_UPDATED".equals(envelope.eventType())) {
                OrderPlacedPayload payload = envelope.payload();
                
                String status = payload.status();
                // We only care about CANCELLED from the orders topic, because FILLED/REJECTED come from trade-events
                if ("CANCELLED".equals(status)) {
                    log.info("Processing notification for cancelled order {}", payload.orderId());
                    processNotification(payload.accountId(), payload.orderId().toString(), payload.side(), payload.quantity(), payload.symbol(), status);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process order event for notifications", e);
        }
    }

    @KafkaListener(topics = "trade-events", groupId = "order-service-notifications-group")
    public void handleTradeEvent(String message) {
        try {
            Envelope<TradeEventPayload> envelope = objectMapper.readValue(message, new TypeReference<Envelope<TradeEventPayload>>() {});
            
            if ("ORDER_FILLED".equals(envelope.eventType()) || "ORDER_REJECTED".equals(envelope.eventType())) {
                TradeEventPayload payload = envelope.payload();
                
                log.info("Processing notification for filled/rejected order {}", payload.orderId());
                processNotification(payload.accountId(), payload.orderId().toString(), payload.side(), payload.quantity(), payload.symbol(), payload.status());
            }
        } catch (Exception e) {
            log.error("Failed to process trade event for notifications", e);
        }
    }

    private void processNotification(Long accountId, String orderId, String side, Integer quantity, String symbol, String status) {
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) return;
        
        Optional<User> userOpt = userRepository.findById(accountOpt.get().getUserId());
        if (userOpt.isEmpty()) return;
        
        User user = userOpt.get();
        if ("EMAIL".equals(user.getNotificationChannel())) {
            sendEmail(user.getEmail(), orderId, side, quantity, symbol, status);
        } else if ("PUSH".equals(user.getNotificationChannel())) {
            log.info("PUSH notification requested for order {}. (In-app notification system handles this).", orderId);
        }
    }

    private void sendEmail(String toEmail, String orderId, String side, Integer quantity, String symbol, String status) {
        String msg = String.format("Your order to %s %d %s was %s.", 
                side, quantity, symbol, status);

        Map<String, Object> body = new HashMap<>();
        body.put("service_id", serviceId);
        body.put("template_id", templateId);
        body.put("user_id", publicKey);
        body.put("accessToken", privateKey);
        
        Map<String, String> templateParams = new HashMap<>();
        templateParams.put("to_email", toEmail);
        templateParams.put("message", msg);
        templateParams.put("otp_code", ""); // Reuse existing OTP template's message param
        body.put("template_params", templateParams);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity("https://api.emailjs.com/api/v1.0/email/send", request, String.class);
            log.info("Email notification sent to {} for order {}", toEmail, orderId);
        } catch (Exception e) {
            log.error("Failed to send EmailJS notification to {}", toEmail, e);
        }
    }
}
