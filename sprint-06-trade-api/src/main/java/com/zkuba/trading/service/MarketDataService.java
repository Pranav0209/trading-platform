package com.zkuba.trading.service;

import java.math.BigDecimal;

public interface MarketDataService {

    BigDecimal getCurrentPrice(String symbol);
}
