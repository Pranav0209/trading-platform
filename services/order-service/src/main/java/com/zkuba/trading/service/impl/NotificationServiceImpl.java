package com.zkuba.trading.service.impl;

import com.zkuba.trading.domain.entity.Account;
import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.repository.AccountRepository;
import com.zkuba.trading.repository.UserRepository;
import com.zkuba.trading.service.NotificationService;
import com.zkuba.trading.service.PriceAlertEvaluator.AlertNotificationPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${emailjs.service-id}")
    private String serviceId;

    @Value("${emailjs.template-id}")
    private String templateId;

    @Value("${emailjs.public-key}")
    private String publicKey;

    @Value("${emailjs.private-key}")
    private String privateKey;

    @Value("${emailjs.api-url:https://api.emailjs.com/api/v1.0/email/send}")
    private String apiUrl;

    public NotificationServiceImpl(
            AccountRepository accountRepository,
            UserRepository userRepository,
            RestTemplate restTemplate) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    public void sendPriceAlertNotification(AlertNotificationPayload payload) {
        try {
            // Validate EmailJS configuration
            if (serviceId == null || templateId == null || publicKey == null || privateKey == null) {
                log.error("EmailJS configuration is incomplete. Service: {}, Template: {}, Public Key: {}, Private Key: {}", 
                        serviceId != null, templateId != null, publicKey != null, privateKey != null);
                throw new IllegalStateException("EmailJS configuration missing - check EMAILJS_* environment variables");
            }

            // Resolve account and user
            Optional<Account> accountOpt = accountRepository.findById(payload.accountId());
            if (accountOpt.isEmpty()) {
                log.warn("Account not found for alert notification: {}", payload.alertId());
                throw new IllegalArgumentException("Account not found");
            }

            Optional<User> userOpt = userRepository.findById(accountOpt.get().getUserId());
            if (userOpt.isEmpty()) {
                log.warn("User not found for alert notification: {}", payload.alertId());
                throw new IllegalArgumentException("User not found");
            }

            User user = userOpt.get();

            // Route by notification channel
            if ("EMAIL".equals(user.getNotificationChannel())) {
                sendEmailAlert(user.getEmail(), payload);
            } else if ("PUSH".equals(user.getNotificationChannel())) {
                log.info("PUSH channel requested for alert {}. Push notifications not yet implemented.", payload.alertId());
            } else {
                log.warn("Unknown notification channel for user {}: {}", user.getId(), user.getNotificationChannel());
            }

        } catch (Exception e) {
            log.error("Error sending price alert notification for alert {}", payload.alertId(), e);
            throw e;
        }
    }

    @Override
    public void sendOrderNotification(OrderNotificationPayload payload) {
        try {
            Optional<Account> accountOpt = accountRepository.findById(payload.accountId());
            if (accountOpt.isEmpty()) {
                log.warn("Account not found for order notification: {}", payload.orderId());
                return;
            }

            Optional<User> userOpt = userRepository.findById(accountOpt.get().getUserId());
            if (userOpt.isEmpty()) {
                log.warn("User not found for order notification: {}", payload.orderId());
                return;
            }

            User user = userOpt.get();

            if ("EMAIL".equals(user.getNotificationChannel())) {
                String subject = "Trading Alert: Order " + payload.status();
                String message = String.format("Your order to %s %d %s was %s.", 
                        payload.side(), payload.quantity(), payload.symbol(), payload.status());
                sendEmail(user.getEmail(), subject, message, payload.orderId());
            } else if ("PUSH".equals(user.getNotificationChannel())) {
                log.info("PUSH notification requested for order {}. Not yet implemented.", payload.orderId());
            }

        } catch (Exception e) {
            log.error("Error sending order notification for order {}", payload.orderId(), e);
        }
    }

    /**
     * Send price alert via EmailJS
     */
    private void sendEmailAlert(String toEmail, AlertNotificationPayload payload) {
        String direction = payload.direction();
        String directionText = "ABOVE".equalsIgnoreCase(direction) ? "rose above" : "fell below";
        String message = String.format(
                "Price alert triggered: %s %s %.2f (current price: %.2f)",
                payload.symbol(),
                directionText,
                payload.threshold(),
                payload.currentPrice()
        );
        String subject = "Trading Alert: " + payload.symbol() + " price alert";
        sendEmail(toEmail, subject, message, String.valueOf(payload.alertId()));
    }

    private void sendEmail(String toEmail, String subject, String message, String referenceId) {
        Map<String, Object> body = new HashMap<>();
        body.put("service_id", serviceId);
        body.put("template_id", templateId);
        body.put("user_id", publicKey);
        body.put("accessToken", privateKey);

        Map<String, String> templateParams = new HashMap<>();
        templateParams.put("to_email", toEmail);
        templateParams.put("message", message);
        templateParams.put("subject", subject);
        body.put("template_params", templateParams);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(apiUrl, request, String.class);
            log.info("Email notification sent to {} for reference {}", toEmail, referenceId);
        } catch (Exception e) {
            log.error("Failed to send email notification to {} for reference {}", toEmail, referenceId, e);
            throw e;
        }
    }
}
