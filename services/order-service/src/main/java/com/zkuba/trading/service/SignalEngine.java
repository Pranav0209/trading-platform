package com.zkuba.trading.service;

import com.zkuba.trading.domain.enums.SignalSource;
import com.zkuba.trading.domain.enums.SignalType;
import com.zkuba.trading.dto.response.SignalResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SignalEngine {

    public SignalResponse generateSignal(String symbol, SignalSource source, IndicatorCalculator.Indicators indicators, Instant priceTimestamp, boolean stale) {
        if (indicators == null) {
            return new SignalResponse(
                    symbol,
                    source,
                    SignalType.INSUFFICIENT_DATA,
                    0,
                    List.of("Not enough historical data to calculate indicators."),
                    Map.of(),
                    stale,
                    priceTimestamp
            );
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();

        // Moving Averages
        if (indicators.sma20() != null) {
            if (indicators.currentPrice() > indicators.sma20()) {
                score++;
                reasons.add("Price is above the 20-day moving average.");
            } else {
                score--;
                reasons.add("Price is below the 20-day moving average.");
            }
            
            if (indicators.sma50() != null) {
                if (indicators.sma20() > indicators.sma50()) {
                    score++;
                    reasons.add("20-day SMA is above the 50-day SMA (Bullish).");
                } else {
                    score--;
                    reasons.add("20-day SMA is below the 50-day SMA (Bearish).");
                }
            }
        }

        // MACD
        if (indicators.macd() != null && indicators.macdSignal() != null) {
            if (indicators.macd() > indicators.macdSignal()) {
                score++;
                reasons.add("MACD is above the signal line (Bullish).");
            } else {
                score--;
                reasons.add("MACD is below the signal line (Bearish).");
            }
        }

        // RSI
        if (indicators.rsi14() != null) {
            if (indicators.rsi14() < 30) {
                score++;
                reasons.add(String.format("RSI(14) is %.2f (Oversold, possible bullish reversal).", indicators.rsi14()));
            } else if (indicators.rsi14() > 70) {
                score--;
                reasons.add(String.format("RSI(14) is %.2f (Overbought, possible bearish reversal).", indicators.rsi14()));
            } else if (indicators.rsi14() > 50) {
                score++;
                reasons.add(String.format("RSI(14) is %.2f (Positive momentum).", indicators.rsi14()));
            } else {
                score--;
                reasons.add(String.format("RSI(14) is %.2f (Weak/Bearish momentum).", indicators.rsi14()));
            }
        }

        // Momentum
        if (indicators.return5d() != null) {
            if (indicators.return5d() > 0) {
                score++;
                reasons.add(String.format("5-day momentum is positive (%.2f%%).", indicators.return5d() * 100));
            } else {
                score--;
                reasons.add(String.format("5-day momentum is negative (%.2f%%).", indicators.return5d() * 100));
            }
        }

        // Volume confirmation
        if (indicators.currentVolume() != null && indicators.averageVolume20d() != null) {
            if (indicators.currentVolume() > indicators.averageVolume20d()) {
                // High volume
                if (indicators.return5d() != null && indicators.return5d() > 0) {
                    score++;
                    reasons.add("High volume confirms recent upward movement.");
                } else if (indicators.return5d() != null && indicators.return5d() < 0) {
                    score--;
                    reasons.add("High volume confirms recent downward movement.");
                }
            } else {
                reasons.add("Volume is below average, reducing signal confidence.");
            }
        }

        SignalType signalType;
        if (score >= 3) {
            signalType = SignalType.BUY;
        } else if (score <= -3) {
            signalType = SignalType.SELL;
        } else {
            signalType = SignalType.HOLD;
        }

        Map<String, Object> indicatorValues = new HashMap<>();
        indicatorValues.put("currentPrice", indicators.currentPrice());
        indicatorValues.put("sma20", indicators.sma20());
        indicatorValues.put("sma50", indicators.sma50());
        indicatorValues.put("rsi14", indicators.rsi14());
        indicatorValues.put("macd", indicators.macd());
        indicatorValues.put("macdSignal", indicators.macdSignal());
        indicatorValues.put("macdHistogram", indicators.macdHistogram());
        indicatorValues.put("bollingerUpper", indicators.bollingerUpper());
        indicatorValues.put("bollingerMiddle", indicators.bollingerMiddle());
        indicatorValues.put("bollingerLower", indicators.bollingerLower());
        indicatorValues.put("return5d", indicators.return5d());
        indicatorValues.put("return20d", indicators.return20d());
        indicatorValues.put("currentVolume", indicators.currentVolume());
        indicatorValues.put("averageVolume20d", indicators.averageVolume20d());

        return new SignalResponse(
                symbol,
                source,
                signalType,
                score,
                reasons,
                indicatorValues,
                stale,
                priceTimestamp
        );
    }
}
