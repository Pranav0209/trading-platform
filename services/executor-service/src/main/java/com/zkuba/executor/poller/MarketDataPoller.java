package com.zkuba.executor.poller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zkuba.executor.client.BatchQuoteResponse;
import com.zkuba.executor.client.CurrencyConverter;
import com.zkuba.executor.client.QuoteClient;
import com.zkuba.executor.client.QuoteResponse;
import com.zkuba.executor.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class MarketDataPoller {

    private static final Logger log = LoggerFactory.getLogger(MarketDataPoller.class);
    private static final int BATCH_SIZE = 25;

    private final QuoteClient quoteClient;
    private final CurrencyConverter conversionClient;
    private final PositionRepository positionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public MarketDataPoller(QuoteClient quoteClient,
            CurrencyConverter conversionClient,
            PositionRepository positionRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.quoteClient = quoteClient;
        this.conversionClient = conversionClient;
        this.positionRepository = positionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Enforces the interval floor in the code rather than documenting one.
     * The Fauxnance quota is 2000 requests per day (approx 1 request per 43.2
     * seconds).
     * We set an absolute floor of 45 seconds to stay safe using SpEL Math.max.
     */

    // runs on a loop forever, min delay of 45 sec
    @Scheduled(fixedDelayString = "#{T(java.lang.Math).max(${poll.interval.seconds:60}, 45) * 1000}")
    public void pollMarketData() {
        // get list of symbols in users positions and watchlist
        List<String> symbols = positionRepository.findUniqueSymbolsOfInterest();
        if (symbols.isEmpty()) {
            log.debug("No symbols of interest found. Skipping market data poll.");
            return;
        }

        log.info("Polling market data for {} symbols...", symbols.size());

        // if user has more than 25, it batches them
        for (int i = 0; i < symbols.size(); i += BATCH_SIZE) {
            List<String> batch = symbols.subList(i, Math.min(symbols.size(), i + BATCH_SIZE));
            fetchAndPublishBatch(batch);
        }
    }

    // broadcasting to market data topic, writes values as json
    private void fetchAndPublishBatch(List<String> batch) {
        try {
            BatchQuoteResponse response = quoteClient.getBatchQuotes(batch);
            if (response != null && response.data() != null && response.data().quotes() != null) {
                for (BatchQuoteResponse.QuoteItem item : response.data().quotes()) {
                    if (item.quote() != null) {
                        QuoteResponse.Data quote = item.quote();
                        
                        // Convert to USD if needed
                        if (quote.currency() != null && !quote.currency().equalsIgnoreCase("USD")) {
                            try {
                                BigDecimal rate = conversionClient.getConversionRate(quote.currency(), "USD");
                                quote = new QuoteResponse.Data(
                                        quote.symbol(),
                                        quote.price().multiply(rate),
                                        quote.bid() != null ? quote.bid().multiply(rate) : null,
                                        quote.ask() != null ? quote.ask().multiply(rate) : null,
                                        "USD",
                                        quote.change() != null ? quote.change().multiply(rate) : null,
                                        quote.changePercent(),
                                        quote.previousClose() != null ? quote.previousClose().multiply(rate) : null,
                                        quote.asOf(),
                                        quote.marketState()
                                );
                            } catch (Exception e) {
                                log.warn("Failed to convert currency for {} from {}", quote.symbol(), quote.currency(), e);
                                // Fallback to raw quote if conversion fails
                            }
                        }
                        
                        String json = objectMapper.writeValueAsString(quote);
                        kafkaTemplate.send("market-data", item.symbol(), json);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch or publish market data for batch {}", batch, e);
        }
    }
}
