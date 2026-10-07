package com.zkuba.executor.service;

import com.zkuba.executor.client.CandleResponse.Candle;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SmartRuleIndicatorCalculator {

    public Double calculateSma(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period) {
            return null;
        }
        double sum = 0;
        for (int index = candles.size() - period; index < candles.size(); index++) {
            sum += candles.get(index).close();
        }
        return sum / period;
    }

    public Double calculatePreviousSma(List<Candle> candles, int period) {
        if (candles == null || candles.size() <= period) {
            return null;
        }
        double sum = 0;
        for (int index = candles.size() - period - 1; index < candles.size() - 1; index++) {
            sum += candles.get(index).close();
        }
        return sum / period;
    }

    public BollingerBandValues calculateBollingerBands(List<Candle> candles, int period, double multiplier) {
        Double sma = calculateSma(candles, period);
        if (sma == null) {
            return null;
        }
        double variance = 0;
        for (int index = candles.size() - period; index < candles.size(); index++) {
            double diff = candles.get(index).close() - sma;
            variance += diff * diff;
        }
        variance /= period;
        double stdDev = Math.sqrt(variance);
        return new BollingerBandValues(sma + (multiplier * stdDev), sma, sma - (multiplier * stdDev));
    }

    public record BollingerBandValues(double upper, double middle, double lower) {}
}