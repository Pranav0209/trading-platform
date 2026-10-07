package com.zkuba.trading.service;

import com.zkuba.trading.client.CandleResponse.Candle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class IndicatorCalculator {

    public record Indicators(
            Double currentPrice,
            Double sma20,
            Double sma50,
            Double rsi14,
            Double macd,
            Double macdSignal,
            Double macdHistogram,
            Double bollingerUpper,
            Double bollingerMiddle,
            Double bollingerLower,
            Double return5d,
            Double return20d,
            Long currentVolume,
            Double averageVolume20d
    ) {}

    public Indicators calculate(List<Candle> candles) {
        if (candles == null || candles.size() < 50) {
            return null; // INSUFFICIENT_DATA
        }

        // Fauxnance candles are in chronological order (oldest to newest) normally, but let's assume they are sorted by date.
        // Fauxnance returns JSON. 
        int n = candles.size();
        Candle current = candles.get(n - 1);
        
        Double currentPrice = current.close();
        Long currentVolume = current.volume();

        Double sma20 = calculateSMA(candles, 20);
        Double sma50 = calculateSMA(candles, 50);

        Double rsi14 = calculateRSI(candles, 14);

        double[] macdResult = calculateMACD(candles, 12, 26, 9);
        Double macd = macdResult[0];
        Double macdSignal = macdResult[1];
        Double macdHistogram = macdResult[2];

        double[] bollinger = calculateBollingerBands(candles, 20, 2.0);
        Double bollingerUpper = bollinger[0];
        Double bollingerMiddle = bollinger[1];
        Double bollingerLower = bollinger[2];

        Double return5d = calculateMomentum(candles, 5);
        Double return20d = calculateMomentum(candles, 20);

        Double avgVolume20d = calculateAverageVolume(candles, 20);

        return new Indicators(
                currentPrice,
                sma20,
                sma50,
                rsi14,
                macd,
                macdSignal,
                macdHistogram,
                bollingerUpper,
                bollingerMiddle,
                bollingerLower,
                return5d,
                return20d,
                currentVolume,
                avgVolume20d
        );
    }

    private Double calculateSMA(List<Candle> candles, int period) {
        if (candles.size() < period) return null;
        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).close();
        }
        return sum / period;
    }

    private Double calculateRSI(List<Candle> candles, int period) {
        if (candles.size() <= period) return null;
        
        double avgGain = 0;
        double avgLoss = 0;
        
        // Initial SMA for first period
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            if (change > 0) avgGain += change;
            else avgLoss -= change; // positive value
        }
        avgGain /= period;
        avgLoss /= period;
        
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1 + rs));
    }

    private double[] calculateMACD(List<Candle> candles, int fast, int slow, int signal) {
        if (candles.size() < slow + signal) return new double[]{0, 0, 0};
        
        double[] emaFast = calculateEMA(candles, fast);
        double[] emaSlow = calculateEMA(candles, slow);
        
        double[] macdLine = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            macdLine[i] = emaFast[i] - emaSlow[i];
        }
        
        double[] signalLine = calculateEMA(macdLine, signal);
        
        double currentMacd = macdLine[candles.size() - 1];
        double currentSignal = signalLine[candles.size() - 1];
        double histogram = currentMacd - currentSignal;
        
        return new double[]{currentMacd, currentSignal, histogram};
    }

    private double[] calculateEMA(List<Candle> candles, int period) {
        double[] ema = new double[candles.size()];
        if (candles.isEmpty()) return ema;
        
        double multiplier = 2.0 / (period + 1);
        ema[0] = candles.get(0).close();
        for (int i = 1; i < candles.size(); i++) {
            ema[i] = (candles.get(i).close() - ema[i-1]) * multiplier + ema[i-1];
        }
        return ema;
    }
    
    private double[] calculateEMA(double[] values, int period) {
        double[] ema = new double[values.length];
        if (values.length == 0) return ema;
        
        double multiplier = 2.0 / (period + 1);
        ema[0] = values[0];
        for (int i = 1; i < values.length; i++) {
            ema[i] = (values[i] - ema[i-1]) * multiplier + ema[i-1];
        }
        return ema;
    }

    private double[] calculateBollingerBands(List<Candle> candles, int period, double stdDevMultiplier) {
        if (candles.size() < period) return new double[]{0, 0, 0};
        
        double sma = calculateSMA(candles, period);
        double variance = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double diff = candles.get(i).close() - sma;
            variance += diff * diff;
        }
        variance /= period;
        double stdDev = Math.sqrt(variance);
        
        return new double[]{
                sma + (stdDevMultiplier * stdDev),
                sma,
                sma - (stdDevMultiplier * stdDev)
        };
    }

    private Double calculateMomentum(List<Candle> candles, int period) {
        if (candles.size() <= period) return null;
        double current = candles.get(candles.size() - 1).close();
        double past = candles.get(candles.size() - 1 - period).close();
        return (current - past) / past;
    }

    private Double calculateAverageVolume(List<Candle> candles, int period) {
        if (candles.size() < period) return null;
        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).volume() != null ? candles.get(i).volume() : 0;
        }
        return sum / period;
    }
}
