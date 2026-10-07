package com.zkuba.trading.service;

import com.zkuba.trading.event.QuotePayload;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MarketDataStreamService {
    private static final Logger log = LoggerFactory.getLogger(MarketDataStreamService.class);
    
    private static class UserEmitter {
        SseEmitter emitter;
        Long userId;
        String accountCurrency;
        
        UserEmitter(SseEmitter emitter, Long userId, String accountCurrency) {
            this.emitter = emitter;
            this.userId = userId;
            this.accountCurrency = accountCurrency;
        }
    }
    
    private final List<UserEmitter> emitters = new CopyOnWriteArrayList<>();
    private final CurrencyConversionService currencyConversionService;

    public MarketDataStreamService(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = currencyConversionService;
    }

    public SseEmitter subscribe(Long userId, String accountCurrency) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        UserEmitter userEmitter = new UserEmitter(emitter, userId, accountCurrency);
        emitters.add(userEmitter);
        
        emitter.onCompletion(() -> emitters.remove(userEmitter));
        emitter.onTimeout(() -> emitters.remove(userEmitter));
        emitter.onError((e) -> emitters.remove(userEmitter));
        
        try {
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            emitters.remove(userEmitter);
        }
        
        return emitter;
    }

    public void broadcast(QuotePayload quote) {
        List<UserEmitter> deadEmitters = new java.util.ArrayList<>();
        for (UserEmitter userEmitter : emitters) {
            try {
                // Convert quote to user's account currency
                QuotePayload convertedQuote = convertQuoteToAccountCurrency(quote, userEmitter.accountCurrency);
                userEmitter.emitter.send(SseEmitter.event().name("quote").data(convertedQuote));
            } catch (IOException e) {
                deadEmitters.add(userEmitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    /**
     * Convert quote from its currency to the user's account currency
     */
    private QuotePayload convertQuoteToAccountCurrency(QuotePayload quote, String accountCurrency) {
        // If quote is already in the account currency, no conversion needed
        if (quote.currency().equals(accountCurrency)) {
            return quote;
        }

        // Get conversion rate from quote currency to account currency
        try {
            java.math.BigDecimal rate = currencyConversionService.getConversionRate(quote.currency(), accountCurrency);
            
            return new QuotePayload(
                    quote.symbol(),
                    quote.price().multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP),
                    quote.bid().multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP),
                    quote.ask().multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP),
                    accountCurrency,
                    quote.change().multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP),
                    quote.changePercent(), // percentage doesn't change
                    quote.previousClose().multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP),
                    quote.asOf(),
                    quote.marketState()
            );
        } catch (Exception e) {
            log.warn("Failed to convert quote {} from {} to {}. Returning original quote.", 
                    quote.symbol(), quote.currency(), accountCurrency, e);
            // Return original quote if conversion fails
            return quote;
        }
    }
}
