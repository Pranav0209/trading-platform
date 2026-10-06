package com.zkuba.trading.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.service.MarketDataStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MarketDataListener {
    private static final Logger log = LoggerFactory.getLogger(MarketDataListener.class);
    
    private final MarketDataStreamService streamService;
    private final ObjectMapper objectMapper;

    public MarketDataListener(MarketDataStreamService streamService, ObjectMapper objectMapper) {
        this.streamService = streamService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "market-data", groupId = "order-service-market-data-group")
    public void onMarketData(String message) {
        try {
            QuotePayload quote = objectMapper.readValue(message, QuotePayload.class);
            streamService.broadcast(quote);
        } catch (Exception e) {
            log.error("Error parsing market data message", e);
        }
    }
}
